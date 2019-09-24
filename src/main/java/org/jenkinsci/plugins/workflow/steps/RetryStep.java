/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.steps;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Executes the body up to N times.
 *
 * @author Kohsuke Kawaguchi
 */
public class RetryStep extends Step implements Serializable {
    
    private final int count;
    private int timeDelay;
    private TimeUnit unit = TimeUnit.SECONDS;
    private boolean useTimeDelay = false;
    
    public int left;

    @DataBoundConstructor
    public RetryStep(int count) {
        this.count = count;
        this.left = count;
    }

    public int getCount() {
        return count;
    }

    @DataBoundSetter public void setUseTimeDelay(boolean useTimeDelay) {
        this.useTimeDelay = useTimeDelay;
    }

    public boolean isUseTimeDelay() {
        return useTimeDelay;
    }

    @DataBoundSetter public void setTimeDelay(int timeDelay) {
        this.timeDelay = timeDelay;
    }

    public int getTimeDelay() {
        return timeDelay;
    }

    @DataBoundSetter public void setUnit(TimeUnit unit) {
        this.unit = unit;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new RetryStepExecution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "retry";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Retry the body up to N times";
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

    }

    private static final long serialVersionUID = 1L;
}
