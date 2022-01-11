package org.jenkinsci.plugins.workflow.support.steps.retry;

import java.io.Serializable;

import javax.annotation.Nonnull;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;

/**
 * {@link IncrementalDelay} allows the delay to gradually get
 * larger for issues that cannot be immediately fixed. The delay
 * starts out at the min and then each round after that the 
 * increment is added to it until it reaches the max, and then
 * the max for the remaining rounds.
 */
@Extension
public class IncrementalDelay extends RetryDelay implements Serializable {

    private final long min;
    private final long max;
    private final long increment;
    private long lastDelay = 0;

    public IncrementalDelay() { 
        super();
        this.increment = 0;
        this.max = 0;
        this.min = 0;
    }

    @DataBoundConstructor
    public IncrementalDelay(long increment, long min, long max) {
        this.increment = increment;
        this.max = max;
        this.min = min;
    }

    public long getIncrement() {
        return increment;
    }

    public long getMin() {
        return min;
    }

    public long getMax() {
        return max;
    }

    @Override
    public long computeRetryDelay() {
        long delay = min;
        if(lastDelay == 0) {
            lastDelay = min;
        } else {
            delay = lastDelay;
            lastDelay = delay + increment;
        } 

        // Check to see if greater than max
        if(lastDelay > max) {
            lastDelay = max;
        }
        return unit.toMillis(lastDelay);
    }

    @Extension @Symbol("incremental")
    public static class DescriptorImpl extends RetryDelayDescriptor {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Incremental";
        }
        private static final long serialVersionUID = 1L;
    }
    private static final long serialVersionUID = 1L;
}