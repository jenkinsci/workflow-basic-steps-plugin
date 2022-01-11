package org.jenkinsci.plugins.workflow.support.steps.retry;

import java.io.Serializable;

import javax.annotation.Nonnull;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;

@Extension
public class FixedDelay extends RetryDelay implements Serializable {

    private final long time;

    public FixedDelay() { 
        super();
        this.time = 0;
    }

    @DataBoundConstructor
    public FixedDelay(long time) {
        this.time = time;
    }

    public long getTime() {
        return time;
    }

    @Override
    public long computeRetryDelay() {
        return unit.toMillis(time);
    }

    @Extension @Symbol("fixed")
    public static class DescriptorImpl extends RetryDelayDescriptor {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Fixed";
        }
        private static final long serialVersionUID = 1L;
    }
    private static final long serialVersionUID = 1L;
}