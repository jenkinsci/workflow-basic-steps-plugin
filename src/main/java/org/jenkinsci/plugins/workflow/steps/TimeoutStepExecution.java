package org.jenkinsci.plugins.workflow.steps;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Main;
import hudson.Util;
import hudson.model.Result;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.CauseOfInterruption;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.LinearBlockHoppingScanner;

@SuppressFBWarnings("SE_INNER_CLASS")
public class TimeoutStepExecution extends AbstractStepExecutionImpl {

    private static final Logger LOGGER = Logger.getLogger(TimeoutStepExecution.class.getName());
    private static final long GRACE_PERIOD = Main.isUnitTest ? /* 5s */5_000 : /* 1m */60_000;

    @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used when starting.")
    private transient final TimeoutStep step;
    private BodyExecution body;
    private transient ScheduledFuture<?> killer;

    private long end = 0;
    /** whether we are forcing the body to end */
    private boolean forcible;

    TimeoutStepExecution(TimeoutStep step, StepContext context) {
        super(context);
        this.step = step;
    }

    @Override
    public boolean start() throws Exception {
        StepContext context = getContext();
        body = context.newBodyInvoker()
                .withCallback(new Callback())
                .start();
        long now = System.currentTimeMillis();
        end = now + step.getUnit().toMillis(step.getTime());
        setupTimer(now);
        return false;   // execution is asynchronous
    }

    @Override
    public void onResume() {
        setupTimer(System.currentTimeMillis());
    }

    private TaskListener listener() {
        try {
            return getContext().get(TaskListener.class);
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, null, x);
            return TaskListener.NULL;
        }
    }

    /**
     * Sets the timer to manage the timeout.
     *
     * @param now Current time in milliseconds.
     */
    private void setupTimer(final long now) {
        long delay = end - now;
        if (delay > 0) {
            if (!forcible) {
                listener().getLogger().println("Timeout set to expire in " + Util.getTimeSpanString(delay));
            }
            killer = Timer.get().schedule(new Runnable() {
                @Override
                public void run() {
                    cancel();
                }
            }, delay, TimeUnit.MILLISECONDS);
        } else {
            listener().getLogger().println("Timeout expired " + Util.getTimeSpanString(- delay) + " ago");
            cancel();
        }
    }

    private void cancel() {
        if (forcible) {
            if (!killer.isCancelled()) {
                listener().getLogger().println("Body did not finish within grace period; terminating with extreme prejudice");
                FlowExecution exec;
                try {
                    exec = getContext().get(FlowExecution.class);
                } catch (IOException | InterruptedException x) {
                    LOGGER.log(Level.WARNING, null, x);
                    return;
                }
                final Throwable death = new FlowInterruptedException(Result.ABORTED, new ExceededTimeout());
                /* Due to JENKINS-25504, this does not accomplish anything beyond what the original body.cancel would have:
                getContext().onFailure(death);
                */
                final ListenableFuture<List<StepExecution>> currentExecutions = exec.getCurrentExecutions(true);
                // TODO would use Futures.addCallback but this is still @Beta in Guava 19 and the Pipeline copy is in workflow-support on which we have no dep
                currentExecutions.addListener(new Runnable() {
                    @Override public void run() {
                        assert currentExecutions.isDone();
                        try {
                            FlowNode outer = getContext().get(FlowNode.class); // timeout
                            for (StepExecution exec : currentExecutions.get()) {
                                FlowNode inner = exec.getContext().get(FlowNode.class); // some deadbeat step, perhaps
                                LinearBlockHoppingScanner scanner = new LinearBlockHoppingScanner();
                                scanner.setup(inner);
                                for (FlowNode enclosing : scanner) {
                                    if (enclosing.equals(outer)) {
                                        exec.getContext().onFailure(death);
                                        break;
                                    }
                                }
                            }
                        } catch (IOException | InterruptedException | ExecutionException x) {
                            LOGGER.log(Level.WARNING, null, x);
                        }
                    }
                }, MoreExecutors.sameThreadExecutor());
            }
        } else {
            listener().getLogger().println("Cancelling nested steps due to timeout");
            body.cancel(new ExceededTimeout());
            forcible = true;
            long now = System.currentTimeMillis();
            end = now + GRACE_PERIOD;
            setupTimer(now);
        }
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        if (body!=null)
            body.cancel(cause);
    }

    @Override public String getStatus() {
        if (killer == null) {
            return "killer task nowhere to be found";
        } else if (killer.isCancelled()) {
            return "killer task was cancelled";
        } else if (killer.isDone()) {
            return "killer task reported done";
        } else {
            long delay = end - System.currentTimeMillis();
            if (delay <= 0) {
                return "overshot by " + Util.getTimeSpanString(-delay);
            }
            String delayS = Util.getTimeSpanString(delay);
            if (forcible) {
                return "body did not yet respond to signal; forcibly killing in " + delayS;
            } else {
                return "body has another " + delayS + " to run";
            }
        }
    }

    private class Callback extends BodyExecutionCallback.TailCall {

        @Override protected void finished(StepContext context) throws Exception {
            if (killer!=null) {
                killer.cancel(true);
                killer = null;
            }
        }

        private static final long serialVersionUID = 1L;

    }

    /**
     * Common cause in this step.
     */
    public static final class ExceededTimeout extends CauseOfInterruption {

        private static final long serialVersionUID = 1L;

        @Override
        public String getShortDescription() {
            return "Timeout has been exceeded";
        }
    }

    private static final long serialVersionUID = 1L;
}
