package org.jenkinsci.plugins.workflow.steps;


import com.google.common.base.Function;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Functions;
import hudson.Util;
import hudson.model.TaskListener;

import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import jenkins.util.Timer;

/**
 * @author Kohsuke Kawaguchi
 */
public class RetryStepExecution extends AbstractStepExecutionImpl {

    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Only used when starting.")
    private transient final RetryStep step;
    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Only used when starting.")
    private transient final int count;
    private transient volatile ScheduledFuture<?> task;

    /** Used to track whether this is timing out on inactivity without needing to reference {@link #step}. */
    private boolean executing = false;
    /** Token for {@link #executing} callbacks. */
    private final String id = UUID.randomUUID().toString();


    @Deprecated
    RetryStepExecution(int count, StepContext context) {
        super(context);
        this.count =count;
        this.step = null;
    }

    RetryStepExecution(@Nonnull RetryStep step, StepContext context) {
        super(context);
        this.step = step;
        this.count = step.getCount();
    }

    @Override public boolean start() throws Exception {
        StepContext context = getContext();
        if(step == null) {
            context.newBodyInvoker()
                .withCallback(new Callback(count))
                .start();
        } else {
            executing = true;
            context.newBodyInvoker()
                .withCallback(new Callback(id,step))
                .start();
        }
        return false;   // execution is asynchronous
    }
    
    @Override public void stop(Throwable cause) throws Exception {
        if (task != null) {
            task.cancel(false);
        }
        super.stop(cause);
    }
    
    @Override public void onResume() {
        if (!executing && step != null) {
            // Restarted while waiting for the timer to go off. Rerun now.
            getContext().newBodyInvoker().withCallback(new Callback(id, step)).start();
            executing = true;
        } // otherwise we are in the middle of the body already, so let it run
    }

    private static void retry(final String id, final StepContext context) {
        StepExecution.applyAll(RetryStepExecution.class, new Function<RetryStepExecution, Void>() {
            @Override public Void apply(@Nonnull RetryStepExecution execution) {
                if (execution.id.equals(id)) {
                    execution.retry(context);
                }
                return null;
            }
        });
    }

    private void retry(StepContext perBodyContext) {
        executing = false;
        getContext().saveState();

        try {
            TaskListener l = getContext().get(TaskListener.class);
            if(step.left>0) {
                long delay = step.getUnit().toMillis(step.getTimeDelay());
                l.getLogger().println(
                    "Will try again after " + 
                    Util.getTimeSpanString(delay));
                task = Timer.get().schedule(new Runnable() {
                    @Override public void run() {
                        task = null;
                        try {
                            l.getLogger().println("Retrying");
                        } catch (Exception x) {
                            getContext().onFailure(x);
                            return;
                        }
                        getContext().newBodyInvoker().withCallback(new Callback(id,step)).start();
                        executing = true;
                    }
                }, delay, TimeUnit.MILLISECONDS);
            }
        } catch (Throwable p) {
            getContext().onFailure(p);
        }
    }

    @Override public String getStatus() {
        if (executing) {
            return "running body";
        } else if (task == null) {
            return "no body, no task, not sure what happened";
        } else if (task.isDone()) {
            return "scheduled task is done, but no body";
        } else if (task.isCancelled()) {
            return "scheduled task was cancelled";
        } else {
            return "waiting to rerun; next recurrence period: " + 
                step.getUnit().toMillis(step.getTimeDelay()) + "ms";
        }
    }

    private static class Callback extends BodyExecutionCallback {

        private final RetryStep step;
        private int left;
        private final String id;

        @Deprecated
        Callback(int count) {
            left = count;
            this.step = null;
            this.id = "-1";
        }

        Callback(String id, RetryStep step) {
            this.id = id;
            this.step = step;
            left = step.getCount();
        }

        @Override
        public void onSuccess(StepContext context, Object result) {
            context.onSuccess(result);
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            try {
                if (t instanceof FlowInterruptedException) {
                    context.onFailure(t);
                    return;
                }
                int remaining = 0;
                if(step != null) {
                    step.left--;
                    remaining = step.left;
                } else { 
                    left--;
                    remaining = left;
                }
                if (remaining>0) {
                    TaskListener l = context.get(TaskListener.class);
                    if (t instanceof AbortException) {
                        l.error(t.getMessage());
                    } else {
                        Functions.printStackTrace(t, l.error("Execution failed"));
                    }
                    if(step != null && !step.isUseTimeDelay()) {
                        l.getLogger().println("Retrying");
                        context.newBodyInvoker().withCallback(this).start();
                    } else {
                        RetryStepExecution.retry(id, context);
                    }
                } else {
                    // No need to print anything in this case, since it will be thrown up anyway.
                    context.onFailure(t);
                }
            } catch (Throwable p) {
                context.onFailure(p);
            }
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
