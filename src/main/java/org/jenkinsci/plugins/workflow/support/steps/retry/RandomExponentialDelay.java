package org.jenkinsci.plugins.workflow.support.steps.retry;

import java.io.Serializable;
import java.util.Random;

import javax.annotation.Nonnull;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;

/**
 * {@link RandomExponentialDelay} The delay starts out at the 
 * 2^0 {@TimeUnit} and then increases 2^x * 1 {@TimeUnit} 
 * each round after until it reaches the max, then each round after
 * it reaches the max, the delay is a random number between 1 and max.
 */
@Extension
public class RandomExponentialDelay extends RetryDelay implements Serializable {

    private final int multiplier;
    private final int max;
    private final int base = 2;
    private boolean largerThanMax = false;
    private int lastMultiplier = 0;
    private Random random = new Random();

    public RandomExponentialDelay() { 
        super();
        this.multiplier = 0;
        this.max = 0;
    }

    @DataBoundConstructor
    public RandomExponentialDelay(int multiplier, int max) {
        this.multiplier = multiplier;
        this.max = max;
    }

    public int getMultiplier() {
        return multiplier;
    }

    public long getMax() {
        return max;
    }

    @Override
    public long computeRetryDelay() {
        long delay = 0;
        if(largerThanMax) {
            delay = 1 + random.nextInt(max);
        } else {
            if(lastMultiplier > 0) {
                lastMultiplier += 1;
            } else {
                lastMultiplier = multiplier;
            }
            delay = ExponentialDelay.powerN(base, lastMultiplier);
    
            // Check to see if greater than max
            if(delay > max) {
                delay = max;
                largerThanMax = true;
            }
        }

        return unit.toMillis(delay);
    }

    @Extension @Symbol("randomExponential")
    public static class DescriptorImpl extends RetryDelayDescriptor {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Random Exponential";
        }
        private static final long serialVersionUID = 1L;
    }
    private static final long serialVersionUID = 1L;
}