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

import hudson.Extension;
import hudson.Util;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.Collections;
import java.util.Set;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.kohsuke.stapler.DataBoundConstructor;

public class UnstableStep extends Step {

    private final String message;

    @DataBoundConstructor
    public UnstableStep(String message) {
        message = Util.fixEmptyAndTrim(message);
        if (message == null) {
            message = "Setting build result to unstable";
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
            getContext().get(FlowNode.class).addAction(new WarningAction(step.message));
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

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            // Run, FlowNode, and TaskListener are always available.
            return Collections.emptySet();
        }
    }
}
