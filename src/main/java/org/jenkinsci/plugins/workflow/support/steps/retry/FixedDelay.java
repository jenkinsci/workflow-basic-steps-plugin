package org.jenkinsci.plugins.workflow.support.steps.retry;

import javax.annotation.Nonnull;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;

@Extension
public class FixedDelay extends RetryDelay {

    private final long delay;

    public FixedDelay() { 
        super();
        this.delay = 0;
    }

    @DataBoundConstructor
    public FixedDelay(long delay) {
        this.delay = delay;
    }

    public long getDelay() {
        return delay;
    }

    @Override
    public long computeRetryDelay() {
        return unit.toMillis(delay);
    }

    @Extension @Symbol("fixed")
    public static class DescriptorImpl extends RetryDelayDescriptor {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Fixed";
        }
    }
}