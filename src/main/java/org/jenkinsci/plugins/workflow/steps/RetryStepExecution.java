package org.jenkinsci.plugins.workflow.steps;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Functions;
import hudson.model.TaskListener;
import jenkins.model.CauseOfInterruption;

/**
 * @author Kohsuke Kawaguchi
 */
public class RetryStepExecution extends AbstractStepExecutionImpl {
    
    @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used when starting.")
    private transient final int count;

    RetryStepExecution(int count, StepContext context) {
        super(context);
        this.count = count;
    }

    @Override
    public boolean start() throws Exception {
        StepContext context = getContext();
        context.newBodyInvoker()
            .withCallback(new Callback(count))
            .start();
        return false;   // execution is asynchronous
    }

    @Override public void onResume() {}

    private static class Callback extends BodyExecutionCallback {

        private int left;

        Callback(int count) {
            left = count;
        }

        /* Could be added, but seems unnecessary, given the message already printed in onFailure:
        @Override public void onStart(StepContext context) {
            try {
                context.get(TaskListener.class).getLogger().println(left + " tries left");
            } catch (Exception x) {
                context.onFailure(x);
            }
        }
        */

        @Override
        public void onSuccess(StepContext context, Object result) {
            context.onSuccess(result);
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            try {
                if (t instanceof FlowInterruptedException && ((FlowInterruptedException)t).isActualInterruption()) {
                    context.onFailure(t);
                    return;
                }
                left--;
                if (left>0) {
                    TaskListener l = context.get(TaskListener.class);
                    if (t instanceof AbortException) {
                        l.error(t.getMessage());
                    } else if (t instanceof FlowInterruptedException) {
                        FlowInterruptedException fie = (FlowInterruptedException) t;
                        fie.handle(context.get(Run.class), l);
                    } else {
                        Functions.printStackTrace(t, l.error("Execution failed"));
                    }
                    l.getLogger().println("Retrying");
                    context.newBodyInvoker().withCallback(this).start();
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
