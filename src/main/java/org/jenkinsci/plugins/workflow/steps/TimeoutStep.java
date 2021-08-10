package org.jenkinsci.plugins.workflow.steps;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Executes the body with a timeout, which will kill the body.
 *
 * @author Kohsuke Kawaguchi
 */
public class TimeoutStep extends Step implements Serializable {

    private final int time;

    private TimeUnit unit = TimeUnit.MINUTES;

    private boolean activity = false;

    @DataBoundConstructor
    public TimeoutStep(int time) {
        this.time = time;
    }

    @DataBoundSetter
    public void setUnit(TimeUnit unit) {
        this.unit = unit;
    }

    public int getTime() {
        return time;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    @DataBoundSetter
    public void setActivity(boolean activity) {
        this.activity = activity;
    }

    public boolean isActivity() {
        return activity;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new TimeoutStepExecution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "timeout";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Enforce time limit";
        }

        public ListBoxModel doFillUnitItems() {
            ListBoxModel r = new ListBoxModel();
            for (TimeUnit unit : TimeUnit.values()) {
                r.add(unit.name());
            }
            return r;
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }

        private static String getUnitString(String tu) {
            switch(tu) {
                case "DAYS":
                    return "d";
                case "HOURS":
                    return "h";
                case "MINUTES":
                    return "m";
                case "SECONDS":
                    return "s";
                case "NANOSECONDS":
                    return "ns";
                case "MICROSECONDS":
                    return "us";
                case "MILLISECONDS":
                    return "ms";
            }
            return "";
        }

        @Override public String argumentsToString(Map<String, Object> namedArgs) {
            Object time = namedArgs.get("time");
            if (time != null) {
                Object activity = namedArgs.get("activity");
                String activityS = activity instanceof Boolean ? activity.toString() : "false";
                Object unit = namedArgs.get("unit");
                if (unit instanceof String) {
                    return time.toString() + getUnitString((String) unit) + ", " + activityS;
                } else {
                    return time.toString() + "s, " + activityS;
                }
            }
            return null;
        }

    }

    private static final long serialVersionUID = 1L;
}
