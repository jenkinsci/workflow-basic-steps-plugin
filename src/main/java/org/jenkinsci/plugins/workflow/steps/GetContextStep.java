/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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
import java.util.Collections;
import java.util.Set;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Obtains a Jenkins API object from the current context.
 */
public class GetContextStep extends Step {

    public final Class<?> type;

    @DataBoundConstructor
    public GetContextStep(Class<?> type) {
        this.type = type;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(type, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "getContext";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Get contextual object from internal APIs";
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.emptySet(); // depends on the instance
        }
    }

    public static class Execution extends SynchronousStepExecution<Object> {

        private static final long serialVersionUID = 1;

        @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Only used when starting.")
        private final transient Class<?> type;

        Execution(Class<?> type, StepContext context) {
            super(context);
            this.type = type;
        }

        @Override
        protected Object run() throws Exception {
            return getContext().get(type);
        }
    }
}
