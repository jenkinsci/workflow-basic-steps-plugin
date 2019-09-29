package org.jenkinsci.plugins.workflow.support.steps.retry;

import java.io.Serializable;

import javax.annotation.Nonnull;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;

/**
 * {@link ExponentialDelay} allows the delay to exponentially grow
 * larger for issues that cannot be immediately fixed. The delay
 * starts out at the min and then increases 2^x * 1 {@TimeUnit} 
 * each round after until it reaches the max, then the max for the
 * remaining rounds.
 */
@Extension
public class ExponentialDelay extends RetryDelay implements Serializable {

    private final long min;
    private final long max;
    private final int multiplier;
    private final int base = 2;
    private int lastMultiplier = 0;

    public ExponentialDelay() { 
        super();
        this.multiplier = 0;
        this.max = 0;
        this.min = 0;
    }

    @DataBoundConstructor
    public ExponentialDelay(int multiplier, long min, long max) {
        this.multiplier = multiplier;
        this.max = max;
        this.min = min;
    }

    public int getMultiplier() {
        return multiplier;
    }

    public long getMin() {
        return min;
    }

    public long getMax() {
        return max;
    }

    @Override
    public long computeRetryDelay() {
        if(lastMultiplier > 0) {
            lastMultiplier += 1;
        } else {
            lastMultiplier = multiplier;
        }
        long delay = powerN(base, lastMultiplier) + min;

        // Check to see if greater than max
        if(delay > max) {
            delay = max;
        }
        return unit.toMillis(delay);
    }

    protected static long powerN(long number, int power){
        long res = 1;
        long sq = number;
        while(power > 0){
            if(power % 2 == 1){
                res *= sq; 
            }
            sq = sq * sq;
            power /= 2;
        }
        return res;
    }

    @Extension @Symbol("exponential")
    public static class DescriptorImpl extends RetryDelayDescriptor {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Exponential";
        }
        private static final long serialVersionUID = 1L;
    }
    private static final long serialVersionUID = 1L;
}