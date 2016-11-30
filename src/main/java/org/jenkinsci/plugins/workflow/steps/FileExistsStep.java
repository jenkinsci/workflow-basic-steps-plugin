/*
 * The MIT License
 *
 * Copyright 2015 Bastian Echterhoelter.
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.FilePath;
import java.util.Collections;
import java.util.Set;


import org.kohsuke.stapler.DataBoundConstructor;

public final class FileExistsStep extends Step {

    private final String file;

    @DataBoundConstructor public FileExistsStep(String file) {
        this.file = file;
    }

    public String getFile() {
        return file;
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new Execution(file, context);
    }

    @Extension public static final class DescriptorImpl extends StepDescriptor {

        @Override public String getFunctionName() {
            return "fileExists";
        }

        @Override public String getDisplayName() {
            return "Verify if file exists in workspace";
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(FilePath.class);
        }

    }

    public static final class Execution extends SynchronousNonBlockingStepExecution<Boolean> {

        @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used when starting.")
        private transient final String file;

        Execution(String file, StepContext context) {
            super(context);
            this.file = file;
        }

        @Override protected Boolean run() throws Exception {
        	return getContext().get(FilePath.class).child(file).exists();
        }

        private static final long serialVersionUID = 1L;

    }

}
