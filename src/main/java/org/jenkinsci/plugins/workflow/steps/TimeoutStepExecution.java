package org.jenkinsci.plugins.workflow.steps;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.CheckForNull;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.actions.TimeoutInfoAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import jenkins.model.CauseOfInterruption;
import jenkins.util.Timer;

/**
 * @author Kohsuke Kawaguchi
 */
@SuppressFBWarnings("SE_INNER_CLASS")
public class TimeoutStepExecution extends AbstractStepExecutionImpl {
    public static final String VARNAME = "timeout";

    @Inject(optional=true)
    private transient TimeoutStep step;
    private BodyExecution body;
    private transient ScheduledFuture<?> killer;

    private String id = null;
    private long start = 0;
    private long timeout;
    private long end = 0;

    @StepContextParameter
    private transient TaskListener listener;

    @Override
    public boolean start() throws Exception {
        id = step.getId();
        timeout = calculateTimeout();
        listener.getLogger().println(String.format(
            "Use %d msec for timeout value",
            timeout
        ));

        StepContext context = getContext();
        body = context.newBodyInvoker()
                .withCallback(new Callback())
                .withContext(EnvironmentExpander.merge(
                    context.get(EnvironmentExpander.class),
                    new EnvironmentExpanderImpl(timeout)
                ))
                .start();
        start = System.currentTimeMillis();
        long now = start;
        end = now + timeout;
        listener.getLogger().println();
        setupTimer(now);
        return false;   // execution is asynchronous
    }

    @Override
    public void onResume() {
        super.onResume();
        setupTimer(System.currentTimeMillis());
    }

    private long calculateTimeout() {
        long baseTimeout = step.getUnit().toMillis(step.getTime());
        if (step.getElastic() > 0) {
            try {
                long elasticTimeout = calculateElasticTimeout();
                if (elasticTimeout > 0) {
                    return elasticTimeout;
                }
            } catch (Exception e) {
                listener.error("Failed to calculate the elastic timeout limit:");
                e.printStackTrace(listener.getLogger());
            }
        }
        return baseTimeout;
    }

    private long calculateElasticTimeout() throws Exception {
        if (StringUtils.isBlank(id)) {
            listener.error(
                "No id is set for timeout block. Cannot calculate elastic timeout limit."
            );
            return 0;
        }
        Run<?, ?> run = getContext().get(Run.class);
        Run<?, ?> previous = run.getPreviousSuccessfulBuild();
        if (previous == null) {
            listener.getLogger().println(
                "No previous successful build to calculate elastic timeout limit."
            );
            return 0;
        }
        TimeoutInfoAction info = extractTimeoutInfoActionWithId(id, previous);
        if (info == null) {
            listener.getLogger().println(
                "No duration time is recorded in the last successful build"
                + " to calculate elastic timeout limit."
            );
            return 0;
        }
        long duration = (long)(step.getElastic() * info.getDuration());
        if (duration <= 0) {
            listener.getLogger().println(
                "Calcuated elastic timeout limit gets 0. Ignored."
            );
            return 0;
        }
        return duration;
    }

    /**
     * Extract the first {@link TimeoutInfoAction} from a run
     *
     * Put in the package scope for testing purpose.
     *
     * @param id id for {@link TimeoutInfoAction}
     * @param run run to extract {@link TimeoutInfoAction} from
     * @return an instance of {@link TimeoutInfoAction} if found, {@code null} if not found
     * @throws IOException 
     */
    @CheckForNull
    /*package*/ static TimeoutInfoAction extractTimeoutInfoActionWithId(String id, Run<?, ?> run) throws IOException {
        if (!(run instanceof FlowExecutionOwner.Executable)) {
            return null;
        }
        FlowExecution execution = ((FlowExecutionOwner.Executable)run).asFlowExecutionOwner().get();
        for (FlowNode node: new FlowGraphWalker(execution)) {
            TimeoutInfoAction action = node.getAction(TimeoutInfoAction.class);
            if (action == null) {
                continue;
            }
            if (action.getId().equals(id)) {
                return action;
            }
        }
        return null;
    }

    /**
     * Sets the timer to manage the timeout.
     *
     * @param now Current time in milliseconds.
     */
    private void setupTimer(final long now) {
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

    @Override
    public void stop(Throwable cause) throws Exception {
        if (body!=null)
            body.cancel(cause);
    }

    private void finished() throws Exception {
        if (killer!=null) {
            killer.cancel(true);
            killer = null;
        }
        long duration = System.currentTimeMillis() - start;
        listener.getLogger().println(String.format(
            "End of timeout block. Elapsed %s msec",
            duration
        ));
        if (!StringUtils.isBlank(id)) {
            // Record execution duration of body
            // * Record even if the build is aborted for timeout
            //     * We use durations only of successful runs
            // * Don't care the down time: there's no way to get that.
            getContext().get(FlowNode.class).addAction(
                new TimeoutInfoAction(
                    id,
                    duration
                )
            );
        }
    }

    private class Callback extends BodyExecutionCallback.TailCall {

        @Override protected void finished(StepContext context) throws Exception {
            TimeoutStepExecution.this.finished();
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

    private static final class EnvironmentExpanderImpl extends EnvironmentExpander {
        private static final long serialVersionUID = 1L;

        private final long timeout;

        public EnvironmentExpanderImpl(long timeout) {
            this.timeout = timeout;
        }

        @Override
        public void expand(EnvVars env) throws IOException, InterruptedException {
            env.put(VARNAME, Long.toString(timeout));
        }
        
    }
 
    private static final long serialVersionUID = 1L;
}
