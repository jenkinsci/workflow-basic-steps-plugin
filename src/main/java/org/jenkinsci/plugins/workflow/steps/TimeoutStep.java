package org.jenkinsci.plugins.workflow.steps;

import hudson.Extension;
import hudson.Util;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import javax.annotation.CheckForNull;

/**
 * Executes the body with a timeout, which will kill the body.
 *
 * @author Kohsuke Kawaguchi
 */
public class TimeoutStep extends AbstractStepImpl implements Serializable {

    private final int time;

    private TimeUnit unit = TimeUnit.MINUTES;

    private String id;

    private float elastic;

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

    /**
     * @param id name for this block
     * @since 2.2
     */
    @DataBoundSetter
    public void setId(@CheckForNull String id) {
        this.id = Util.fixEmptyAndTrim(id);
    }

    /**
     * @return name for this block
     * @since 2.2
     */
    @CheckForNull
    public String getId() {
        return id;
    }

    /**
     * @param elastic percentage to decide timeout limit from last builds.
     *     0 or negative values to disable.
     * @since 2.2
     */
    @DataBoundSetter
    public void setElastic(float elastic) {
        this.elastic = elastic;
    }

    /**
     * @return percentage to decide timeout limit from last builds.
     *     0 or negative values to disable
     * @since 2.2
     */
    public float getElastic() {
        return elastic;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(TimeoutStepExecution.class);
        }

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

    }

    private static final long serialVersionUID = 1L;
}
