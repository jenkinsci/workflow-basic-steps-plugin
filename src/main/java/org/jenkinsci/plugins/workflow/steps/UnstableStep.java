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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class UnstableStep extends Step {

    private final String message;

    @DataBoundConstructor
    public UnstableStep(String message) {
        message = Util.fixEmptyAndTrim(message);
        if (message == null) {
            throw new IllegalArgumentException("A non-empty message is required");
        }
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new UnstableStepExecution(this, context);
    }

    private static class UnstableStepExecution extends SynchronousStepExecution<Void> {
        private static final long serialVersionUID = 1L;
        private transient final UnstableStep step;

        private UnstableStepExecution(UnstableStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            getContext().get(FlowNode.class).addOrReplaceAction(new WarningAction(Result.UNSTABLE).withMessage(step.message));
            getContext().get(Run.class).setResult(Result.UNSTABLE);
            getContext().get(TaskListener.class).getLogger().append("WARNING: ").println(step.message);
            return null;
        }
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "unstable";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Set stage result to unstable";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            Set<Class<?>> context = new HashSet<>();
            Collections.addAll(context, FlowNode.class, Run.class, TaskListener.class);
            return Collections.unmodifiableSet(context);
        }

        public FormValidation doCheckMessage(@QueryParameter String message) {
            if (Util.fixEmptyAndTrim(message) == null) {
                return FormValidation.error("Message must be non-empty");
            }
            return FormValidation.ok();
        }
    }
}
