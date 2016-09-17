package org.jenkinsci.plugins.workflow.steps;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.console.ConsoleLogFilter;
import hudson.console.LineTransformationOutputStream;
import hudson.model.Run;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jenkins.model.CauseOfInterruption;
import jenkins.util.Timer;

/**
 * @author Kohsuke Kawaguchi
 */
@SuppressFBWarnings("SE_INNER_CLASS")
public class TimeoutStepExecution extends AbstractStepExecutionImpl {
    @Inject(optional=true)
    private transient TimeoutStep step;
    private BodyExecution body;
    private transient ScheduledFuture<?> killer;

    private long timeout = 0;
    private long end = 0;

    @Override
    public boolean start() throws Exception {
        StepContext context = getContext();
        BodyInvoker bodyInvoker = context.newBodyInvoker()
                .withCallback(new Callback());

        if (step.isActivity()) {
            bodyInvoker = bodyInvoker.withContext(
                BodyInvoker.mergeConsoleLogFilters(
                    context.get(ConsoleLogFilter.class),
                    new ConsoleLogFilterImpl(new ResetCallback())
                )
            );
        }

        body = bodyInvoker.start();
        timeout = step.getUnit().toMillis(step.getTime());
        resetTimer();
        return false;   // execution is asynchronous
    }

    @Override
    public void onResume() {
        super.onResume();
        setupTimer(System.currentTimeMillis(), false);
    }

    /**
     * Sets the timer to manage the timeout.
     *
     * @param now Current time in milliseconds.
     * @param force reset timer if already set
     */
    private void setupTimer(final long now, boolean force) {
        if (killer != null) {
            if (!force) {
                // already set
                return;
            }
            killer.cancel(true);
            killer = null;
        }

        if (end > now) {
            killer = Timer.get().schedule(new Runnable() {
                @Override
                public void run() {
                    body.cancel(new ExceededTimeout());
                }
            }, end - now, TimeUnit.MILLISECONDS);
        } else {
            body.cancel(new ExceededTimeout());
        }
    }

    private void resetTimer() {
        long now = System.currentTimeMillis();
        end = now + timeout;
        setupTimer(now, true);
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        if (body!=null)
            body.cancel(cause);
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

    private class ResetCallback implements Serializable {
        private static final long serialVersionUID = 1L;

        private void logWritten() {
            resetTimer();
        }
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

    private static class ConsoleLogFilterImpl extends ConsoleLogFilter implements Serializable {
        private static final long serialVersionUID = 1L;

        private final ResetCallback callback;

        public ConsoleLogFilterImpl(ResetCallback callback) {
            this.callback = callback;
        }

        @Override
        public OutputStream decorateLogger(@SuppressWarnings("rawtypes") Run build, final OutputStream logger)
            throws IOException, InterruptedException
        {
            return new LineTransformationOutputStream() {
                @Override
                protected void eol(byte[] b, int len) throws IOException {
                    logger.write(b, 0, len);
                    callback.logWritten();
                }

                @Override
                public void flush() throws IOException {
                    super.flush();
                    logger.flush();
                }

                @Override
                public void close() throws IOException {
                    super.close();
                    logger.close();
                }
            };
        }
    }

    private static final long serialVersionUID = 1L;
}
