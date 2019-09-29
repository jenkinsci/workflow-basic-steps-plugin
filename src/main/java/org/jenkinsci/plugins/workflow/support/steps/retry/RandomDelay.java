package org.jenkinsci.plugins.workflow.support.steps.retry;

import java.io.Serializable;
import java.util.Random;

import javax.annotation.Nonnull;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;

/**
 * {@link RandomDelay} allows the delay to vary between the min and max range.
 */
@Extension
public class RandomDelay extends RetryDelay implements Serializable {

    private final int min;
    private final int max;
    private Random random = new Random();

    public RandomDelay() { 
        super();
        this.max = 0;
        this.min = 0;
    }

    @DataBoundConstructor
    public RandomDelay(int min, int max) {
        this.max = max;
        this.min = min;
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
        if(min == max) {
            delay = min;
        } else {
            delay = random.nextInt(max-min) + min;
        }

        return unit.toMillis(delay);
    }

    @Extension @Symbol("random")
    public static class DescriptorImpl extends RetryDelayDescriptor {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Random";
        }
        private static final long serialVersionUID = 1L;
    }
    private static final long serialVersionUID = 1L;
}