/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.Util;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.util.Set;

/**
 * Runs a block, and if that block fails, prints a message, marks the build as {@link Result#SUCCESS}, adds a
 * {@link WarningAction} to the step, and then continues execution normally.
 *
 * @see CatchErrorStep
 */
public class IgnoreErrorStep extends Step implements CatchExecutionOptions {
    private static final long serialVersionUID = 1L;

    private final String message;
    private boolean catchInterruptions = true;

    @DataBoundConstructor
    public IgnoreErrorStep(String message) {
        message = Util.fixEmptyAndTrim(message);
        if (message == null) {
            throw new IllegalArgumentException("A non-empty message is required");
        }
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public Result getBuildResultOnError() {
        return Result.SUCCESS;
    }

    @Override
    public Result getStepResultOnError() {
        return Result.UNSTABLE;
    }

    @Override
    public boolean isCatchInterruptions() {
        return catchInterruptions;
    }

    @DataBoundSetter
    public void setCatchInterruptions(boolean catchInterruptions) {
        this.catchInterruptions = catchInterruptions;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new CatchErrorStep.Execution(context, this);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "ignoreError";
        }

        @Override
        public String getDisplayName() {
            return "Catch error and set build result as success and stage result to unstable";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(FlowNode.class, Run.class, TaskListener.class);
        }

        public FormValidation doCheckMessage(@QueryParameter String message) {
            if (Util.fixEmptyAndTrim(message) == null) {
                return FormValidation.error("Message must be non-empty");
            }
            return FormValidation.ok();
        }
    }
}
