/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.support.steps.stash;

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.flow.StashManager;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

public class StashStep extends Step {

    private final @Nonnull String name;
    private @CheckForNull String includes;
    private @CheckForNull String excludes;
    private boolean useDefaultExcludes = true;
    private boolean allowEmpty = false;

    @DataBoundConstructor public StashStep(@Nonnull String name) {
        Jenkins.checkGoodName(name);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getIncludes() {
        return includes;
    }

    @DataBoundSetter public void setIncludes(String includes) {
        this.includes = Util.fixEmpty(includes);
    }

    public String getExcludes() {
        return excludes;
    }

    @DataBoundSetter public void setExcludes(String excludes) {
        this.excludes = Util.fixEmpty(excludes);
    }

    public boolean isUseDefaultExcludes() {
        return useDefaultExcludes;
    }

    @DataBoundSetter public void setUseDefaultExcludes(boolean useDefaultExcludes) {
        this.useDefaultExcludes = useDefaultExcludes;
    }

    public boolean isAllowEmpty() {
        return allowEmpty;
    }

    @DataBoundSetter public void setAllowEmpty(boolean allowEmpty) {
        this.allowEmpty = allowEmpty;
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    public static class Execution extends SynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L;

        private transient final StashStep step;

        Execution(StashStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override protected Void run() throws Exception {
            StashManager.stash(getContext().get(Run.class), step.name, getContext().get(FilePath.class), getContext().get(TaskListener.class), step.includes, step.excludes,
                    step.useDefaultExcludes, step.allowEmpty);
            return null;
        }

    }

    @Extension public static class DescriptorImpl extends StepDescriptor {

        @Override public String getFunctionName() {
            return "stash";
        }

        @Override public String getDisplayName() {
            return "Stash some files to be used later in the build";
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, FilePath.class, TaskListener.class);
        }

        @Override public String argumentsToString(Map<String, Object> namedArgs) {
            Object name = namedArgs.get("name");
            return name instanceof String ? (String) name : null;
        }

    }

}
