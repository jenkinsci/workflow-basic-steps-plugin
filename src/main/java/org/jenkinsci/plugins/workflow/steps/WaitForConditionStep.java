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

import com.google.common.base.Function;
import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import jenkins.util.Timer;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public final class WaitForConditionStep extends Step {
    
    static final long DEFAULT_MIN_RECURRENCE_PERIOD = 250; // ¼s
    static final long DEFAULT_MAX_RECURRENCE_PERIOD = 15000; // ¼min
    
    private long minRecurrencePeriod = DEFAULT_MIN_RECURRENCE_PERIOD;
    private long maxRecurrencePeriod = DEFAULT_MAX_RECURRENCE_PERIOD;
    private TimeUnit unit = TimeUnit.MILLISECONDS;
    
    @DataBoundConstructor
    public WaitForConditionStep() {}

    @DataBoundSetter
    public void setUnit(TimeUnit unit) {
        this.unit = unit;
    }

    @DataBoundSetter    
    public void setMinRecurrencePeriod(long minRecurrencePeriod) {
        this.minRecurrencePeriod = minRecurrencePeriod;
    }
    
    @DataBoundSetter    
    public void setMaxRecurrencePeriod(long maxRecurrencePeriod) {
        this.maxRecurrencePeriod = maxRecurrencePeriod;
    }

    public long getMinRecurrencePeriod() {
        return minRecurrencePeriod;
    }

    public long getMaxRecurrencePeriod() {
        return maxRecurrencePeriod;
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    public static final class Execution extends AbstractStepExecutionImpl {

        private static final long serialVersionUID = 1;
        private volatile BodyExecution body;
        private transient volatile ScheduledFuture<?> task;
        /**
         * TODO JENKINS-26148 is there no cleaner way of finding the StepExecution that created a BodyExecutionCallback?
         * @see #retry(String, StepContext)
         */
        private final String id = UUID.randomUUID().toString();
        private static final float RECURRENCE_PERIOD_BACKOFF = 1.2f;
        long currentRecurrencePeriod;
        long maxRecurrencePeriod;
        private transient final WaitForConditionStep step;
        
        Execution(WaitForConditionStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override public boolean start() throws Exception {
            body = getContext().newBodyInvoker().withCallback(new Callback(id)).start();
            this.currentRecurrencePeriod = step.unit.toMillis(step.getMinRecurrencePeriod());
            this.maxRecurrencePeriod = step.unit.toMillis(step.getMaxRecurrencePeriod());
            return false;
        }

        @Override public void stop(Throwable cause) throws Exception {
            if (body != null) {
                body.cancel(cause);
            }
            if (task != null) {
                task.cancel(false);
                getContext().onFailure(cause);
            }
        }

        @Override public void onResume() {
            if (body == null) {
                // Restarted while waiting for the timer to go off. Rerun now.
                body = getContext().newBodyInvoker().withCallback(new Callback(id)).start();
            } // otherwise we are in the middle of the body already, so let it run
        }

        private static void retry(final String id, final StepContext context) {
            StepExecution.applyAll(Execution.class, new Function<Execution, Void>() {
                @Override public Void apply(@Nonnull Execution execution) {
                    if (execution.id.equals(id)) {
                        execution.retry(context);
                    }
                    return null;
                }
            });
        }

        private void retry(StepContext perBodyContext) {
            body = null;
            getContext().saveState();
            try {
                perBodyContext.get(TaskListener.class).getLogger().println("Will try again after " + Util.getTimeSpanString(currentRecurrencePeriod));
            } catch (Exception x) {
                getContext().onFailure(x);
                return;
            }
            task = Timer.get().schedule(new Runnable() {
                @Override public void run() {
                    task = null;
                    body = getContext().newBodyInvoker().withCallback(new Callback(id)).start();
                }
            }, currentRecurrencePeriod, TimeUnit.MILLISECONDS);
            currentRecurrencePeriod = Math.min((long)(currentRecurrencePeriod * RECURRENCE_PERIOD_BACKOFF), maxRecurrencePeriod);
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
                return "waiting to rerun; next recurrence period: " + currentRecurrencePeriod + "ms";
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

        @Override public String getDisplayName() {
            return "Wait for condition";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }
        
        public ListBoxModel doFillUnitItems() {
            ListBoxModel r = new ListBoxModel();
            for (TimeUnit unit : TimeUnit.values()) {
                r.add(unit.name());
            }
            return r;
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }

    }

}
