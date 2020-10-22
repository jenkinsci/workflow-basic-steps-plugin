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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.flow.StashManager;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class UnstashStep extends Step {

    private final @Nonnull String name;

    private boolean verbose;

    @DataBoundConstructor public UnstashStep(@Nonnull String name) {
        Jenkins.checkGoodName(name);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    public static class Execution extends SynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L;

//        @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used when starting.")
//        private transient final String name;
        private transient final UnstashStep step;

        Execution(UnstashStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        private void verbose(List<File> files) throws Exception {
            if (step.verbose) {
                files.stream().forEach(file -> {
                    PrintStream logger = null;
                    try {
                        String name = getContext().get(Run.class).getParent().getName();
                        logger = getContext().get(TaskListener.class).getLogger();
                        String gibberish = file.getCanonicalPath().split("/" + name + "/")[0];
                        logger.println("unstashed file " + StringUtils.substring(file.getCanonicalPath(), gibberish.length()));
                    } catch (IOException | InterruptedException e) {
                        logger.println("unstashed file " + file.getName());
                    }

                });
            }
        }

        @Override protected Void run() throws Exception {
//            verbose();
            List<File> unstash = StashManager.unstash(getContext().get(Run.class), step.name, getContext().get(FilePath.class), getContext().get(Launcher.class), getContext().get(EnvVars.class), getContext().get(TaskListener.class));
            verbose(unstash);
            System.out.println(" Giles "+ unstash.size());
            return null;
        }

    }

    @Extension public static class DescriptorImpl extends StepDescriptor {

        @Override public String getFunctionName() {
            return "unstash";
        }

        @Override public String getDisplayName() {
            return "Restore files previously stashed";
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, FilePath.class, Launcher.class, EnvVars.class, TaskListener.class);
        }

    }

}
