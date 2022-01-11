package org.jenkinsci.plugins.workflow.support.steps.retry;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.structs.SymbolLookup;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;

public abstract class RetryDelay extends AbstractDescribableImpl<RetryDelay> implements ExtensionPoint {

    @Nonnull protected TimeUnit unit = TimeUnit.SECONDS;

    @DataBoundSetter public void setUnit(@Nonnull TimeUnit unit) {
        this.unit = unit;
    }

    @Nonnull public TimeUnit getUnit() {
        return unit;
    }
    
    /**
     * Computes the delay in MILLISECONDS
     * @return Returns an integer for the time delay in MILLISECONDS
     */
    public abstract long computeRetryDelay();

    public static abstract class RetryDelayDescriptor extends Descriptor<RetryDelay> implements Serializable {

        public @Nonnull String getName() {
            Set<String> symbolValues = SymbolLookup.getSymbolValue(this);
            if (symbolValues.isEmpty()) {
                throw new IllegalArgumentException("Retry Delay descriptor class " + this.getClass().getName()
                        + " does not have a @Symbol and does not override getName().");
            }
            return symbolValues.iterator().next();
        }

        /**
         * Get all {@link RetryDelayDescriptor}s.
         * 
         * @return a List of {@link RetryDelayDescriptor}s
         */
        public static List<RetryDelayDescriptor> all() {
            ExtensionList<RetryDelayDescriptor> descs = ExtensionList.lookup(RetryDelayDescriptor.class);
            return descs.stream().sorted(Comparator.comparing(RetryDelayDescriptor::getName)).collect(Collectors.toList());
        }

        public ListBoxModel doFillUnitItems() {
            ListBoxModel r = new ListBoxModel();
            for (TimeUnit unit : TimeUnit.values()) {
                r.add(unit.name());
            }
            return r;
        }
        private static final long serialVersionUID = 1L;
    }
}