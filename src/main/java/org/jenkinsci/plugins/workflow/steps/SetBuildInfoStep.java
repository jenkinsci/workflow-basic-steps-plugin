/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.util.ListBoxModel;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

public final class SetBuildInfoStep extends Step {

    private final RunPropertySetter setting;

    @DataBoundConstructor
    public SetBuildInfoStep(RunPropertySetter setting) {
        this.setting = setting;
    }

    public RunPropertySetter getSetting() {
        return setting;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    private static class Execution extends SynchronousStepExecution<Void> {
        private static final long serialVersionUID = 1;

        private transient final SetBuildInfoStep step;

        Execution(SetBuildInfoStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        public Void run() throws Exception {
            Run r = getContext().get(Run.class);
            if (r != null) {
                step.setting.set(r);
            }
            return null;
        }
    }

    @Extension public static final class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "setBuildInfo";
        }

        @Override public String getDisplayName() {
            return "Modify properties of the current build";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(Run.class);
        }
    }

    @Restricted(NoExternalUse.class)
    public static abstract class RunPropertySetter extends AbstractDescribableImpl<RunPropertySetter> {
        public abstract void set(Run r) throws Exception;
        public static abstract class DescriptorImpl extends Descriptor<RunPropertySetter> { }
    }

    public static class DescriptionSetter extends RunPropertySetter {
        private final String description;

        @DataBoundConstructor
        public DescriptionSetter(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public void set(Run r) throws Exception {
            r.setDescription(description);
        }

        @Symbol("description")
        @Extension public static class DescriptorImpl extends RunPropertySetter.DescriptorImpl {
            @Override public String getDisplayName() {
                return "Description";
            }
        }
    }

    public static class DisplayNameSetter extends RunPropertySetter {
        private final String displayName;

        @DataBoundConstructor
        public DisplayNameSetter(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public void set(Run r) throws Exception {
            r.setDisplayName(displayName);
        }

        @Symbol("displayName")
        @Extension public static class DescriptorImpl extends RunPropertySetter.DescriptorImpl {
            @Override public String getDisplayName() {
                return "Display Name";
            }
        }
    }

    public static class KeepLogSetter extends RunPropertySetter {
        private final boolean keepLog;

        @DataBoundConstructor
        public KeepLogSetter(boolean keepLog) {
            this.keepLog = keepLog;
        }

        public boolean getKeepLog() {
            return keepLog;
        }

        @Override
        public void set(Run r) throws Exception {
            r.keepLog(keepLog);
        }

        @Symbol("keepLog")
        @Extension public static class DescriptorImpl extends RunPropertySetter.DescriptorImpl {
            @Override public String getDisplayName() {
                return "Keep Log";
            }
        }
    }

    public static class ResultSetter extends RunPropertySetter {
        private final String result;

        @DataBoundConstructor
        public ResultSetter(String result) {
            this.result = result;
        }

        public String getResult() {
            return result;
        }

        @Override
        public void set(Run r) throws Exception {
            r.setResult(Result.fromString(result));
        }

        @Symbol("result")
        @Extension public static class DescriptorImpl extends RunPropertySetter.DescriptorImpl {
            @Override public String getDisplayName() {
                return "Result";
            }
            public ListBoxModel doFillResultItems() {
                return Stream.of(Result.SUCCESS, Result.UNSTABLE, Result.FAILURE, Result.NOT_BUILT, Result.ABORTED)
                        .map(r -> new ListBoxModel.Option(r.toString(), r.toString()))
                        .collect(Collectors.toCollection(ListBoxModel::new));
            }
        }
    }

}
