/*
 * The MIT License
 *
 * Copyright 2015 Cloudbees, Inc.
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
import hudson.FilePath;
import java.util.Collections;
import java.util.Set;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Simple step that will wipe out the current working directory in a workflows workspace.
 */
public final class DeleteDirStep extends Step {


    @DataBoundConstructor 
    public DeleteDirStep() {
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(context);
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "deleteDir";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Recursively delete the current directory from the workspace";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(FilePath.class);
        }

    }

    public static final class Execution extends SynchronousNonBlockingStepExecution<Void> {

        Execution(StepContext context) {
            super(context);
        }

        @Override
        protected Void run() throws Exception {
            getContext().get(FilePath.class).deleteRecursive();
            return null;
        }

        private static final long serialVersionUID = 1L;

    }

}
