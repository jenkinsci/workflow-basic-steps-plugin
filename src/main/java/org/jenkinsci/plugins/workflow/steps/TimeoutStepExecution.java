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
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.model.CauseOfInterruption;
import jenkins.security.SlaveToMasterCallable;
import jenkins.util.SystemProperties;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.LinearBlockHoppingScanner;

@SuppressFBWarnings("SE_INNER_CLASS")
public class TimeoutStepExecution extends AbstractStepExecutionImpl {

    private static final Logger LOGGER = Logger.getLogger(TimeoutStepExecution.class.getName());
    private static final long GRACE_PERIOD = Main.isUnitTest ? /* 5s */5_000 : /* 1m */60_000;

    @SuppressFBWarnings(value="MS_SHOULD_BE_FINAL")
    public static /* not final */ boolean forceInterruption = SystemProperties.getBoolean(TimeoutStepExecution.class.getName() + ".forceInterruption");

    @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used when starting.")
    private transient final TimeoutStep step;
    private BodyExecution body;
    private transient ScheduledFuture<?> killer;

    private long timeout;
    private long end = 0;

    /** Used to track whether this is timing out on inactivity without needing to reference {@link #step}. */
    private final boolean activity;

    /**
     * Whether we are forcing the body to end.
     *
     * Transient so that after a restart, we always try to stop the body normally before forcibly
     * terminating it so that this step's state is set up correctly for the forced termination.
     */
    private transient boolean forcible;

    /** Token for {@link #activity} callbacks. */
    private final String id;

    TimeoutStepExecution(TimeoutStep step, StepContext context) {
        super(context);
        this.step = step;
        this.activity = step.isActivity();
        id = activity ? UUID.randomUUID().toString() : null;
        timeout = step.getUnit().toMillis(step.getTime());
    }

    @Override
    public boolean start() throws Exception {
        StepContext context = getContext();
        BodyInvoker bodyInvoker = context.newBodyInvoker()
                .withCallback(new Callback());

        if (step.isActivity()) {
            bodyInvoker = bodyInvoker.withContext(
                    BodyInvoker.mergeConsoleLogFilters(
                            context.get(ConsoleLogFilter.class),
                            new ConsoleLogFilterImpl2(id, timeout)
                    )
            );
        }

        body = bodyInvoker.start();
        resetTimer();
        return false;   // execution is asynchronous
    }

    @Override
    public void onResume() {
        setupTimer(System.currentTimeMillis(), false);
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
     * @param forceReset reset timer if already set
     */
    private void setupTimer(final long now, boolean forceReset) {
        // Used to track whether we should be logging the timeout setup/reset - for activity resets, we don't actually
        // want to log the "Timeout set to expire..." line every single time.
        boolean resettingKiller = false;

        if (killer != null) {
            if (!forceReset) {
                // already set
                return;
            }
            resettingKiller = true;
            killer.cancel(true);
            killer = null;
        }
        long delay = end - now;
        if (delay > 0) {
            if (!forcible && !resettingKiller) {
                if (activity) {
                    listener().getLogger().println("Timeout set to expire after " + Util.getTimeSpanString(delay) + " without activity");
                } else {
                    listener().getLogger().println("Timeout set to expire in " + Util.getTimeSpanString(delay));
                }
            }
            killer = Timer.get().schedule(this::cancel, delay, TimeUnit.MILLISECONDS);
        } else {
            listener().getLogger().println("Timeout expired " + Util.getTimeSpanString(- delay) + " ago");
            cancel();
        }
    }

    private void resetTimer() {
        long now = System.currentTimeMillis();
        end = now + timeout;
        setupTimer(now, true);
    }

    private void cancel() {
        FlowNode flowNode = null;
        try {
            flowNode = getContext().get(FlowNode.class);
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.WARNING, null, e);
        }
        String nodeId = flowNode != null ? flowNode.getId() : null;
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
                final Throwable death = new FlowInterruptedException(Result.ABORTED, new ExceededTimeout(nodeId));
                /* Due to JENKINS-25504, this does not accomplish anything beyond what the original body.cancel would have:
                getContext().onFailure(death);
                */
                final ListenableFuture<List<StepExecution>> currentExecutions = exec.getCurrentExecutions(true);
                // TODO would use Futures.addCallback but this is still @Beta in Guava 19 and the Pipeline copy is in workflow-support on which we have no dep
                currentExecutions.addListener(() -> {
                    assert currentExecutions.isDone();
                    try {
                        FlowNode outer = getContext().get(FlowNode.class); // timeout
                        for (StepExecution exec1 : currentExecutions.get()) {
                            FlowNode inner = exec1.getContext().get(FlowNode.class); // some deadbeat step, perhaps
                            LinearBlockHoppingScanner scanner = new LinearBlockHoppingScanner();
                            scanner.setup(inner);
                            for (FlowNode enclosing : scanner) {
                                if (enclosing.equals(outer)) {
                                    exec1.getContext().onFailure(death);
                                    break;
                                }
                            }
                        }
                    } catch (IOException | InterruptedException | ExecutionException x) {
                        LOGGER.log(Level.WARNING, null, x);
                    }
                }, newExecutorService());
            }
        } else {
            listener().getLogger().println("Cancelling nested steps due to timeout");
            body.cancel(new ExceededTimeout(nodeId));
            forcible = true;
            timeout = GRACE_PERIOD;
            resetTimer();
        }
    }

    /**
     * Returns an {@link ExecutorService} to be used as a parameter in other methods. It calls
     * {@code MoreExecutors#newDirectExecutorService} or falls back to {@code
     * MoreExecutors#sameThreadExecutor} for compatibility with older (&lt; 18.0) versions of Guava.
     *
     * @since TODO
     */
    private static ExecutorService newExecutorService() {
        try {
            try {
                // Guava older than 18
                Method method = MoreExecutors.class.getMethod("sameThreadExecutor");
                return (ExecutorService) method.invoke(null);
            } catch (NoSuchMethodException e) {
                // TODO Invert this to prefer the newer Guava method once Guava is upgraded in
                // Jenkins core.
                Method method = MoreExecutors.class.getMethod("newDirectExecutorService");
                return (ExecutorService) method.invoke(null);
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
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

        @Override
        public void onFailure(StepContext context, Throwable t) {
            if (t instanceof FlowInterruptedException && !forceInterruption) {
                /*
                 * Check whether the FlowInterruptedException has propagated past the corresponding
                 * timeout step.
                 */
                FlowNode flowNode = null;
                try {
                    flowNode = getContext().get(FlowNode.class);
                } catch (IOException | InterruptedException e) {
                    LOGGER.log(Level.WARNING, null, e);
                }
                if (flowNode != null) {
                    String nodeId = flowNode.getId();
                    for (CauseOfInterruption cause : ((FlowInterruptedException) t).getCauses()) {
                        if (cause instanceof ExceededTimeout) {
                            ExceededTimeout exceededTimeout = (ExceededTimeout) cause;
                            if (nodeId.equals(exceededTimeout.getNodeId())) {
                                ((FlowInterruptedException) t).setActualInterruption(false);
                            }
                        }
                    }
                }
            }
            super.onFailure(context, t);
        }

        private static final long serialVersionUID = 1L;

    }

    /**
     * Common cause in this step.
     */
    public static final class ExceededTimeout extends CauseOfInterruption {

        private static final long serialVersionUID = 1L;

        /**
         * Flow node ID of the corresponding timeout block. Could be {@code null} for exceptions
         * serialized before the fix for JENKINS-51454.
         */
        private final String nodeId;

        @Deprecated
        public ExceededTimeout() {
            this(null);
        }

        public ExceededTimeout(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }

        @Override
        public String getShortDescription() {
            return "Timeout has been exceeded";
        }
    }

    private static final class ResetTimer extends SlaveToMasterCallable<Void, RuntimeException> {

        private static final long serialVersionUID = 1L;

        private final @NonNull String id;

        ResetTimer(@NonNull String id) {
            this.id = id;
        }

        @Override public Void call() throws RuntimeException {
            StepExecution.applyAll(TimeoutStepExecution.class, e -> {
                if (id.equals(e.id)) {
                    e.resetTimer();
                }
                return null;
            });
            return null;
        }

    }

    private static class ConsoleLogFilterImpl2 extends ConsoleLogFilter implements /* TODO Remotable */ Serializable {
        private static final long serialVersionUID = 1L;

        private final @NonNull String id;
        private final long timeout;
        private transient @CheckForNull Channel channel;

        ConsoleLogFilterImpl2(@NonNull String id, long timeout) {
            this.id = id;
            this.timeout = timeout;
        }

        private Object readResolve() {
            channel = Channel.current();
            return this;
        }

        @Override
        public OutputStream decorateLogger(@SuppressWarnings("rawtypes") Run build, final OutputStream logger)
                throws IOException, InterruptedException {
            // TODO if channel == null, we can safely ResetTimer.call synchronously from eol and skip the Tick
            AtomicBoolean active = new AtomicBoolean();
            OutputStream decorated = new LineTransformationOutputStream() {
                @Override
                protected void eol(byte[] b, int len) throws IOException {
                    logger.write(b, 0, len);
                    active.set(true);
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
            new Tick(active, new WeakReference<>(decorated), timeout, channel, id).schedule();
            return decorated;
        }
    }

    private static final class Tick implements Runnable {
        private final AtomicBoolean active;
        private final Reference<?> stream;
        private final long timeout;
        private final @CheckForNull Channel channel;
        private final @NonNull String id;
        Tick(AtomicBoolean active, Reference<?> stream, long timeout, @CheckForNull Channel channel, @NonNull String id) {
            this.active = active;
            this.stream = stream;
            this.timeout = timeout;
            this.channel = channel;
            this.id = id;
        }
        @Override
        public void run() {
            if (stream.get() == null) {
                // Not only idle but gone—stop the timer.
                return;
            }
            boolean currentlyActive = active.getAndSet(false);
            if (currentlyActive) {
                Callable<Void, RuntimeException> resetTimer = new ResetTimer(id);
                if (channel != null) {
                    try {
                        channel.call(resetTimer);
                    } catch (Exception x) {
                        LOGGER.log(Level.WARNING, null, x);
                    }
                } else {
                    resetTimer.call();
                }
                schedule();
            } else {
                // Idle at the moment, but check well before the timeout expires in case new output appears.
                schedule(timeout / 10);
            }
        }
        void schedule() {
            schedule(timeout / 2); // less than the full timeout, to give some grace period, but in the same ballpark to avoid overhead
        }
        private void schedule(long delay) {
            Timer.get().schedule(this, delay, TimeUnit.MILLISECONDS);
        }
    }

    /** @deprecated only here for serial compatibility */
    @Deprecated
    public interface ResetCallback extends Serializable {
        void logWritten();
    }

    /** @deprecated only here for serial compatibility */
    @Deprecated
    private class ResetCallbackImpl implements ResetCallback {
        private static final long serialVersionUID = 1L;
        @Override public void logWritten() {
            resetTimer();
        }
    }

    /** @deprecated only here for serial compatibility */
    @Deprecated
    private static class ConsoleLogFilterImpl extends ConsoleLogFilter implements /* TODO Remotable */ Serializable {
        private static final long serialVersionUID = 1L;

        private final ResetCallback callback;

        ConsoleLogFilterImpl(ResetCallback callback) {
            this.callback = callback;
        }

        private Object writeReplace() {
            Channel ch = Channel.current();
            return ch == null ? this : new ConsoleLogFilterImpl(ch.export(ResetCallback.class, callback));
        }

        @Override
        public OutputStream decorateLogger(@SuppressWarnings("rawtypes") Run build, final OutputStream logger)
                throws IOException, InterruptedException {
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
