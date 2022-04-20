package org.jenkinsci.plugins.workflow.steps;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Main;
import hudson.Util;
import hudson.console.ConsoleLogFilter;
import hudson.console.LineTransformationOutputStream;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.function.LongBinaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.CauseOfInterruption;
import jenkins.util.SystemProperties;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.LinearBlockHoppingScanner;
import org.jenkinsci.plugins.workflow.steps.TimeoutStepExecution.ExceededTimeout;

public class TimeoutStepExecutionThreadSafe extends AbstractStepExecutionImpl {

    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL")
    public static /* not final */ boolean forceInterruption = SystemProperties.getBoolean(TimeoutStepExecution.class.getName() + ".forceInterruption");

    private static final long serialVersionUID = 2L;
    private static final Logger LOGGER = Logger.getLogger(TimeoutStepExecutionThreadSafe.class.getName());
    private static final String ACTIVITY_PRECISION_PROPERTY_NAME = TimeoutStepExecution.class.getName() + ".activityPrecision";
    private static final long ACTIVITY_PRECISION_IN_MILLISECONDS = 1000L;
    private static final String ACTIVITY_NOTIFY_WAIT_RATIO_PROPERTY_NAME = TimeoutStepExecution.class.getName() + ".activityNotifyWaitRatio";
    private static final String ACTIVITY_NOTIFY_WAIT_RATIO = String.valueOf(0.8);

    private final String id;
    private final Timeout timeout;

    TimeoutStepExecutionThreadSafe(TimeoutStep step, StepContext context) {
        super(context);
        id = UUID.randomUUID().toString().replace("-", "");
        timeout = createTimeout(id, step, context);
    }

    private static Timeout createTimeout(String id, TimeoutStep step, StepContext context) {
        long time = step.getUnit().toMillis(step.getTime());
        if (step.isActivity()) {
            long precision = SystemProperties.getLong(ACTIVITY_PRECISION_PROPERTY_NAME, ACTIVITY_PRECISION_IN_MILLISECONDS);
            double waitRatio = Double.parseDouble(SystemProperties.getString(ACTIVITY_NOTIFY_WAIT_RATIO_PROPERTY_NAME, ACTIVITY_NOTIFY_WAIT_RATIO));
            return new ActivityTimeout(id, context, time, precision, waitRatio);
        }
        return new AbsoluteTimeout(id, context, time);
    }

    @Override
    public boolean start() throws Exception {
        timeout.startStepExecution();
        return false;
    }

    @Override
    public void onResume() {
        timeout.resume();
    }

    @Override
    public String getStatus() {
        return timeout.getStatus();
    }

    private abstract static class Timeout implements Serializable {

        private static final long serialVersionUID = 1L;
        private static final long GRACE_PERIOD_MILLISECONDS = Main.isUnitTest ? 5_000 : 60_000;

        protected final String id;
        protected final StepContext context;
        private BodyExecution body;
        private long forceKillTimestamp;

        public Timeout(String id, StepContext context) {
            this.id = id;
            this.context = context;
        }

        public void startStepExecution() throws IOException, InterruptedException {
            BodyInvoker bodyInvoker = setup(context.newBodyInvoker());
            bodyInvoker = bodyInvoker.withCallback(new Callback(this));
            body = bodyInvoker.start();
            start();
        }

        protected BodyInvoker setup(BodyInvoker bodyInvoker) throws IOException, InterruptedException {
            return bodyInvoker;
        }

        protected abstract void start();

        public abstract void resume();

        public abstract void stop();

        public String getStatus() {
            long now = System.currentTimeMillis();
            long delay = getEndTimestamp() - now;
            if (delay > 0) {
                return "body has another " + Util.getTimeSpanString(delay) + " to run";
            }
            String message = "body did not yet respond to the signal, overshot by " + Util.getTimeSpanString(-delay);
            if (forceKillTimestamp == 0) {
                return message;
            }
            message += " (forcibly killing ";
            long forceDelay = forceKillTimestamp - now;
            if (forceDelay > 0) {
                message += "in " + Util.getTimeSpanString(forceDelay);
            } else {
                message += "is in progress for " + Util.getTimeSpanString(-forceDelay);
            }
            message += ')';
            return message;
        }

        protected abstract long getEndTimestamp();

        protected void reachTimeout() {
            log("Cancelling nested steps due to timeout");
            body.cancel(new ExceededTimeout(getFromContext(FlowNode.class).map(FlowNode::getId).orElse(null)));
            forceKillTimestamp = System.currentTimeMillis() + GRACE_PERIOD_MILLISECONDS;
            Timer.get().schedule(this::killForcefully, GRACE_PERIOD_MILLISECONDS, TimeUnit.MILLISECONDS);
        }

        private void killForcefully() {
            if (body.isDone()) {
                return;
            }
            log("Body did not finish within grace period; terminating with extreme prejudice");
            getFromContext(FlowExecution.class).ifPresent(exec -> {
                Throwable death = new FlowInterruptedException(Result.ABORTED, new ExceededTimeout(getFromContext(FlowNode.class).map(FlowNode::getId).orElse(null)));
                // Due to JENKINS-25504, this does not accomplish anything beyond what the original body.cancel would have: getContext().onFailure(death);
                ListenableFuture<List<StepExecution>> currentExecutions = exec.getCurrentExecutions(true);
                currentExecutions.addListener(() -> {
                    try {
                        FlowNode timeoutNode = context.get(FlowNode.class);
                        for (StepExecution stepExecution : currentExecutions.get()) {
                            FlowNode innerStepNode = stepExecution.getContext().get(FlowNode.class);
                            LinearBlockHoppingScanner scanner = new LinearBlockHoppingScanner();
                            scanner.setup(innerStepNode);
                            for (FlowNode enclosing : scanner) {
                                if (enclosing.equals(timeoutNode)) {
                                    stepExecution.getContext().onFailure(death);
                                    break;
                                }
                            }
                        }
                    } catch (IOException | InterruptedException | ExecutionException e) {
                        LOGGER.log(Level.WARNING, null, e);
                    }
                }, MoreExecutors.newDirectExecutorService());
            });
        }

        protected void log(String message) {
            getFromContext(TaskListener.class).orElse(TaskListener.NULL).getLogger().println(message + " (id: " + id + ')');
            LOGGER.log(Level.FINE, "[{0}] {1}", new Object[]{id, message});
        }

        protected <T> Optional<T> getFromContext(Class<T> clazz) {
            try {
                return Optional.ofNullable(context.get(clazz));
            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.WARNING, null, e);
                return Optional.empty();
            }
        }

        private static class Callback extends BodyExecutionCallback.TailCall {

            private static final long serialVersionUID = 1L;

            private final Timeout timeout;

            public Callback(Timeout timeout) {
                this.timeout = timeout;
            }

            @Override
            protected void finished(StepContext context) {
                timeout.stop();
            }

            @Override
            public void onFailure(StepContext context, Throwable t) {
                if (t instanceof FlowInterruptedException && !forceInterruption) {
                    // check whether the FlowInterruptedException has propagated past the corresponding timeout step
                    timeout.getFromContext(FlowNode.class).ifPresent(flowNode -> {
                        FlowInterruptedException fie = (FlowInterruptedException) t;
                        for (CauseOfInterruption cause : fie.getCauses()) {
                            if (cause instanceof ExceededTimeout) {
                                ExceededTimeout exceededTimeout = (ExceededTimeout) cause;
                                if (flowNode.getId().equals(exceededTimeout.getNodeId())) {
                                    fie.setActualInterruption(false);
                                }
                            }
                        }
                    });
                }
                super.onFailure(context, t);
            }
        }
    }

    private static class AbsoluteTimeout extends Timeout {

        private static final long serialVersionUID = 1L;

        private long time;
        private long end;
        private transient ScheduledFuture<?> killer;

        public AbsoluteTimeout(String id, StepContext context, long time) {
            super(id, context);
            this.time = time;
        }

        @Override
        protected void start() {
            log("Timeout set to expire in " + Util.getTimeSpanString(time));
            end = System.currentTimeMillis() + time;
            scheduleKiller(time);
        }

        private synchronized void scheduleKiller(long delay) {
            killer = Timer.get().schedule(() -> reachTimeout(), delay, TimeUnit.MILLISECONDS);
        }

        @Override
        public void resume() {
            time = end - System.currentTimeMillis();
            if (time <= 0) {
                reachTimeout();
            } else {
                scheduleKiller(time);
            }
        }

        @Override
        public synchronized void stop() {
            killer.cancel(false);
        }

        @Override
        protected long getEndTimestamp() {
            return end;
        }
    }

    private static class ActivityTimeout extends Timeout {

        private static final long serialVersionUID = 1L;

        private final long time;
        private final long precision;
        private final double waitRatio;
        private final LongAccumulator lastActivity = new LongAccumulator(new MaxFunction(), 0);
        private transient ScheduledFuture<?> timer;
        private boolean stopped;

        public ActivityTimeout(String id, StepContext context, long time, long precision, double waitRatio) {
            super(id, context);
            this.time = time;
            this.precision = precision;
            this.waitRatio = waitRatio;
        }

        @Override
        protected BodyInvoker setup(BodyInvoker bodyInvoker) throws IOException, InterruptedException {
            return bodyInvoker.withContext(
                BodyInvoker.mergeConsoleLogFilters(
                    context.get(ConsoleLogFilter.class),
                    new NotifierConsoleLogFilter(this)
                )
            );
        }

        @Override
        public void start() {
            log("Timeout set to expire after " + Util.getTimeSpanString(time) + " without activity");
            lastActivity.accumulate(System.currentTimeMillis());
            scheduleTimer(time);
        }

        private synchronized void scheduleTimer(long delay) {
            if (stopped) {
                return;
            }
            timer = Timer.get().schedule(() -> checkTimer(System.currentTimeMillis()), delay, TimeUnit.MILLISECONDS);
        }

        private void checkTimer(long now) {
            long timestamp = lastActivity.get();
            long delay = time - (now - timestamp) + precision;
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "[" + id + "] checkTimer: now = " + now + ", timestamp = " + timestamp + ", delay = " + delay);
            }
            if (delay <= 0) {
                reachTimeout();
            } else {
                scheduleTimer(delay);
            }
        }

        @Override
        public void resume() {
            checkTimer(System.currentTimeMillis());
        }

        @Override
        public synchronized void stop() {
            stopped = true;
            timer.cancel(false);
        }

        @Override
        protected long getEndTimestamp() {
            return lastActivity.get() + time;
        }

        public void onActivity(long timestamp) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "[" + id + "] onActivity: now = " + System.currentTimeMillis() + ", timestamp = " + timestamp);
            }
            lastActivity.accumulate(timestamp);
        }

        private static class MaxFunction implements LongBinaryOperator, Serializable {

            private static final long serialVersionUID = 1L;

            @Override
            public long applyAsLong(long left, long right) {
                return Math.max(left, right);
            }
        }

        private static class NotifierConsoleLogFilter extends ConsoleLogFilter implements Serializable {

            private static final long serialVersionUID = 1L;

            private final String id;
            private final long time;

            public NotifierConsoleLogFilter(ActivityTimeout timeout) {
                this.id = timeout.id;
                this.time = (long) (timeout.time * timeout.waitRatio);
            }

            @Override
            public OutputStream decorateLogger(Run build, OutputStream logger) {
                ActivityListener listener = new ActivityListener(id, time);
                return new LineTransformationOutputStream.Delegating(logger) {
                    @Override
                    protected void eol(byte[] b, int len) throws IOException {
                        out.write(b, 0, len);
                        listener.onNewLine(System.currentTimeMillis());
                    }
                };
            }

            private static class ActivityListener implements Runnable {

                private final String id;
                private final long time;
                private final AtomicLong lastReceivedTimestamp = new AtomicLong();
                private long lastSentTimestamp;

                public ActivityListener(String id, long time) {
                    this.id = id;
                    this.time = time;
                }

                private void onNewLine(long when) {
                    if (lastReceivedTimestamp.getAndSet(when) == 0) {
                        tryNotify();
                    }
                }

                @Override
                public synchronized void run() {
                    if (!lastReceivedTimestamp.compareAndSet(lastSentTimestamp, 0)) {
                        tryNotify();
                    }
                }

                private synchronized void tryNotify() {
                    long timestamp = lastReceivedTimestamp.get();
                    if (timestamp == 0) {
                        log("timestamp is 0");
                        return;
                    }

                    if (timestamp == lastSentTimestamp) {
                        log("timestamp has been sent earlier", timestamp);
                        return;
                    }

                    notify(timestamp);
                    lastSentTimestamp = timestamp;
                    long delay = time - (System.currentTimeMillis() - lastSentTimestamp);
                    if (delay > 0) {
                        schedule(delay);
                    } else {
                        run();
                    }
                }

                private void notify(long timestamp) {
                    log("notify step execution", timestamp);
                    StepExecution.applyAll(TimeoutStepExecutionThreadSafe.class, stepExecution -> {
                        if (id.equals(stepExecution.id)) {
                            ((ActivityTimeout) stepExecution.timeout).onActivity(timestamp);
                        }
                        return null;
                    });
                }

                private void schedule(long delay) {
                    log("schedule timer");
                    Timer.get().schedule(this, delay, TimeUnit.MILLISECONDS);
                }

                private void log(String message) {
                    log(message, -1);
                }

                private void log(String message, long timestamp) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        String fullMessage = "[" + id + "][" + this.hashCode() + "] " + message + ", now = " + System.currentTimeMillis();
                        if (timestamp > -1) {
                            fullMessage += ", timestamp = " + timestamp;
                        }
                        LOGGER.log(Level.FINE, fullMessage);
                    }
                }
            }
        }
    }
}
