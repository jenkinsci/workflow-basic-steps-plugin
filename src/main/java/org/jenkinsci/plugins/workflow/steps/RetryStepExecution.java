package org.jenkinsci.plugins.workflow.steps;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Functions;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.List;
import org.jenkinsci.plugins.workflow.flow.ErrorCondition;

/**
 * @author Kohsuke Kawaguchi
 */
public class RetryStepExecution extends AbstractStepExecutionImpl {
    
    @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used when starting.")
    private transient final int count;

    @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used when starting.")
    private transient final @CheckForNull List<ErrorCondition> conditions;

    RetryStepExecution(int count, StepContext context, List<ErrorCondition> conditions) {
        super(context);
        this.count = count;
        this.conditions = conditions;
    }

    @Override
    public boolean start() throws Exception {
        StepContext context = getContext();
        context.newBodyInvoker()
            .withCallback(new Callback(count, conditions))
            .start();
        return false;   // execution is asynchronous
    }

    @Override public void onResume() {}

    private static class Callback extends BodyExecutionCallback {

        private int left;
        private final @CheckForNull List<ErrorCondition> conditions;

        Callback(int count, List<ErrorCondition> conditions) {
            left = count;
            this.conditions = conditions;
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
                left--;
                TaskListener l = context.get(TaskListener.class);
                if (left > 0 && matchesConditions(t, context)) {
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

        private boolean matchesConditions(Throwable t, StepContext context) throws IOException, InterruptedException {
            if (conditions == null || conditions.isEmpty()) {
                return !(t instanceof FlowInterruptedException) || !((FlowInterruptedException) t).isActualInterruption();
            }
            for (ErrorCondition ec : conditions) {
                if (ec.test(t, context)) {
                    return true;
                }
            }
            return false;
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
