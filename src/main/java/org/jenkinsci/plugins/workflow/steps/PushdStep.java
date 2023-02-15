/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.support.steps.FilePathDynamicContext;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collections;
import java.util.Set;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

/**
 * Temporarily changes the working directory.
 */
public class PushdStep extends Step {

    private final String path;

    @DataBoundConstructor public PushdStep(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new Execution(path, context);
    }

    @Extension public static final class DescriptorImpl extends StepDescriptor {

        @Override public String getFunctionName() {
            return "dir";
        }

        @NonNull
        @Override public String getDisplayName() {
            return "Change current directory";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class, FilePath.class, FlowNode.class);
        }
        
        @Override public Set<? extends Class<?>> getProvidedContext() {
            return Collections.singleton(FilePath.class);
        }

    }

    public static class Execution extends AbstractStepExecutionImpl {
        
        @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used when starting.")
        private transient final String path;

        Execution(String path, StepContext context) {
            super(context);
            this.path = path;
        }

        @Override public boolean start() throws Exception {
            FilePath dir = getContext().get(FilePath.class).child(path);
            getContext().get(TaskListener.class).getLogger().println("Running in " + dir);
            getContext().newBodyInvoker()
                    // TODO perhaps should just be moved into workflow-durable-task-step?
                    .withContext(FilePathDynamicContext.createContextualObject(dir, getContext().get(FlowNode.class)))
                    // Could use a dedicated BodyExecutionCallback here if we wished to print a message at the end ("Returning to ${cwd}"):
                    .withCallback(BodyExecutionCallback.wrap(getContext()))
                    .start();
            return false;
        }

        @Override public void onResume() {}

        private static final long serialVersionUID = 1L;

    }

}
