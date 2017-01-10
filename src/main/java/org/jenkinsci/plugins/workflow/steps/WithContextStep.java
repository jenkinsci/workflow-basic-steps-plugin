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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.LauncherDecorator;
import hudson.console.ConsoleLogFilter;
import java.util.Collections;
import java.util.Set;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Supplies a contextual Jenkins API object to a block.
 */
public class WithContextStep extends Step {

    public final Object context;

    @DataBoundConstructor public WithContextStep(Object context) {
        this.context = context;
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new Execution(this.context, context);
    }

    @Extension public static class DescriptorImpl extends StepDescriptor {

        @Override public String getFunctionName() {
            return "withContext";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override public String getDisplayName() {
            return "Use contextual object from internal APIs within a block";
        }

        @Override public boolean isAdvanced() {
            return true;
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return Collections.emptySet();
        }

    }

    public static class Execution extends AbstractStepExecutionImpl {

        private static final long serialVersionUID = 1;

        @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used when starting.")
        private transient Object obj;

        Execution(Object obj, StepContext context) {
            super(context);
            this.obj = obj;
        }

        @Override public boolean start() throws Exception {
            StepContext context = getContext();
            if (obj instanceof ConsoleLogFilter) {
                obj = BodyInvoker.mergeConsoleLogFilters(context.get(ConsoleLogFilter.class), (ConsoleLogFilter) obj);
            } else if (obj instanceof LauncherDecorator) {
                obj = BodyInvoker.mergeLauncherDecorators(context.get(LauncherDecorator.class), (LauncherDecorator) obj);
            } else if (obj instanceof EnvironmentExpander) {
                obj = EnvironmentExpander.merge(context.get(EnvironmentExpander.class), (EnvironmentExpander) obj);
            }
            context.newBodyInvoker().withContext(obj).withCallback(BodyExecutionCallback.wrap(context)).start();
            return false;
        }

        @Override public void stop(Throwable cause) throws Exception {
            getContext().onFailure(cause);
        }

        @Override public void onResume() {}

    }

}
