/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.steps;

import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.util.Timer;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public final class WaitForConditionStep extends Step {

    static final long MIN_RECURRENCE_PERIOD = 250; // ¼s
    static final long MAX_RECURRENCE_PERIOD = 15000; // ¼min

    private long initialRecurrencePeriod = MIN_RECURRENCE_PERIOD;
    private boolean quiet = false;

    @DataBoundConstructor public WaitForConditionStep() {}

    @DataBoundSetter
    public void setInitialRecurrencePeriod(long initialRecurrencePeriod) {
        this.initialRecurrencePeriod = Math.max(MIN_RECURRENCE_PERIOD, Math.min(initialRecurrencePeriod, MAX_RECURRENCE_PERIOD));
    }

    public long getInitialRecurrencePeriod() {
        return initialRecurrencePeriod;
    }

    @DataBoundSetter
    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    public boolean getQuiet() { return quiet; }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new Execution(context, initialRecurrencePeriod, this.quiet);
    }

    public static final class Execution extends AbstractStepExecutionImpl {

        private static final long serialVersionUID = 1;
        private volatile BodyExecution body; // TODO could be replaced with a simple boolean flag
        private transient volatile ScheduledFuture<?> task;
        /**
         * TODO JENKINS-26148 is there no cleaner way of finding the StepExecution that created a BodyExecutionCallback?
         * @see #retry(String, StepContext)
         */
        private final String id = UUID.randomUUID().toString();
        private static final float RECURRENCE_PERIOD_BACKOFF = 1.2f;
        private long initialRecurrencePeriod;
        long recurrencePeriod;
        private final boolean quiet;

        Execution(StepContext context, long initialRecurrencePeriod, boolean quiet) {
            super(context);
            this.initialRecurrencePeriod = initialRecurrencePeriod;
            recurrencePeriod = initialRecurrencePeriod;
            this.quiet = quiet;
        }

        private Object readResolve() {
            // in case we are deserializing an older version of this object prior to this field being added
            if (initialRecurrencePeriod == 0) {
                initialRecurrencePeriod = MIN_RECURRENCE_PERIOD;
            }
            return this;
        }

        @Override public boolean start() throws Exception {
            body = getContext().newBodyInvoker().withCallback(new Callback(id)).start();
            return false;
        }

        @Override public void stop(@NonNull Throwable cause) throws Exception {
            if (task != null) {
                task.cancel(false);
            }
            super.stop(cause);
        }

        @Override public void onResume() {
            recurrencePeriod = initialRecurrencePeriod;
            if (body == null) {
                // Restarted while waiting for the timer to go off. Rerun now.
                body = getContext().newBodyInvoker().withCallback(new Callback(id)).start();
            } // otherwise we are in the middle of the body already, so let it run
        }

        private static void retry(final String id, final StepContext context) {
            StepExecution.acceptAll(Execution.class, execution -> {
                if (execution.id.equals(id)) {
                    execution.retry(context);
                }
            });
        }

        private void retry(StepContext perBodyContext) {
            body = null;
            getContext().saveState();
            if (!this.quiet) {
                try {
                    perBodyContext.get(TaskListener.class).getLogger().println("Will try again after " + Util.getTimeSpanString(recurrencePeriod));
                } catch (Exception x) {
                    getContext().onFailure(x);
                    return;
                }
            }
            task = Timer.get().schedule(() -> {
                task = null;
                body = getContext().newBodyInvoker().withCallback(new Callback(id)).start();
            }, recurrencePeriod, TimeUnit.MILLISECONDS);
            recurrencePeriod = Math.min((long)(recurrencePeriod * RECURRENCE_PERIOD_BACKOFF), MAX_RECURRENCE_PERIOD);
        }

        @Override public String getStatus() {
            if (body != null) {
                return "running body";
            } else if (task == null) {
                return "no body, no task, not sure what happened";
            } else if (task.isDone()) {
                return "scheduled task task done, but no body";
            } else if (task.isCancelled()) {
                return "scheduled task was cancelled";
            } else {
                return "waiting to rerun; next recurrence period: " + recurrencePeriod + "ms";
            }
        }

    }

    private static final class Callback extends BodyExecutionCallback {

        private static final long serialVersionUID = 1;
        private final String id;

        Callback(String id) {
            this.id = id;
        }

        @Override public void onSuccess(final StepContext context, Object result) {
            if (!(result instanceof Boolean)) {
                context.onFailure(new ClassCastException("body return value " + result + " is not boolean"));
                return;
            }
            if ((Boolean) result) {
                context.onSuccess(null);
                return;
            }
            Execution.retry(id, context);
        }

        @Override public void onFailure(StepContext context, Throwable t) {
            context.onFailure(t);
        }

    }

    @Extension public static final class DescriptorImpl extends StepDescriptor {

        @Override public String getFunctionName() {
            return "waitUntil";
        }

        @NonNull
        @Override public String getDisplayName() {
            return "Wait for condition";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }

    }

}
