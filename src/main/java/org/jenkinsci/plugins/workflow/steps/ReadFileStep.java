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

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;

import java.io.InputStream;
import java.util.Collections;
import java.util.Set;


import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public final class ReadFileStep extends Step {

    /*package*/ static final String BASE64_ENCODING = "Base64";

    private final String file;
    private String encoding;

    @DataBoundConstructor public ReadFileStep(String file) {
        // Normally pointless to verify that this is a relative path, since shell steps can anyway read and write files anywhere on the slave.
        // Could be necessary in case a plugin installs a {@link LauncherDecorator} which keeps commands inside some kind of jail.
        // In that case we would need some API to determine that such a jail is in effect and this validation must be enforced.
        // But just checking the path is anyway not sufficient (due to crafted symlinks); would need to check the final resulting path.
        // Same for WriteFileStep, PushdStep, FileExistsStep.
        this.file = file;
    }

    public String getFile() {
        return file;
    }

    public String getEncoding() {
        return encoding;
    }

    @DataBoundSetter public void setEncoding(String encoding) {
        this.encoding = Util.fixEmptyAndTrim(encoding);
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    @Extension public static final class DescriptorImpl extends StepDescriptor {

        @Override public String getFunctionName() {
            return "readFile";
        }

        @Override public String getDisplayName() {
            return "Read file from workspace";
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(FilePath.class);
        }

    }

    public static final class Execution extends SynchronousNonBlockingStepExecution<String> {

        private transient final ReadFileStep step;

        Execution(ReadFileStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override protected String run() throws Exception {
            try (InputStream is = getContext().get(FilePath.class).child(step.file).read()) {
                if (BASE64_ENCODING.equals(step.encoding)) {
                    return Base64.encodeBase64String(IOUtils.toByteArray(is));
                } else {
                    return IOUtils.toString(is, step.encoding);
                }
            }
        }

        private static final long serialVersionUID = 1L;

    }

}
