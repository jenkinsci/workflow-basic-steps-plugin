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
import hudson.slaves.WorkspaceList;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Returns the working directory path.
 *
 * Used like:
 *
 * <pre>
 * node {
 *     def x = pwd() // where is my workspace?
 * }
 * </pre>
 */
public class PwdStep extends Step {

    private boolean tmp;

    @DataBoundConstructor public PwdStep() {}

    public boolean isTmp() {
        return tmp;
    }

    @DataBoundSetter public void setTmp(boolean tmp) {
        this.tmp = tmp;
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new Execution(tmp, context);
    }

    @Extension public static final class DescriptorImpl extends StepDescriptor {

        @Override public String getFunctionName() {
            return "pwd";
        }

        @NonNull
        @Override public String getDisplayName() {
            return "Determine current directory";
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(FilePath.class);
        }

        @Override public String argumentsToString(@NonNull Map<String, Object> namedArgs) {
            return null; // "true" is not a reasonable description
        }

    }

    public static class Execution extends SynchronousStepExecution<String> {
        
        @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used when starting.")
        private transient final boolean tmp;

        Execution(boolean tmp, StepContext context) {
            super(context);
            this.tmp = tmp;
        }

        @Override protected String run() throws Exception {
            FilePath cwd = getContext().get(FilePath.class);
            Objects.requireNonNull(cwd);
            if (tmp) {
                cwd = WorkspaceList.tempDir(cwd);
                if (cwd == null) {
                    throw new IOException("Failed to set up a temporary directory.");
                }
            }
            return cwd.getRemote();
        }

        private static final long serialVersionUID = 1L;

    }

}
