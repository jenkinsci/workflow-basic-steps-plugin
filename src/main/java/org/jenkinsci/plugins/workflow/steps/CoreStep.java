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

import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A step that runs a {@link SimpleBuildStep} as defined in Jenkins core.
 */
public final class CoreStep extends Step {

    public final SimpleBuildStep delegate;

    @DataBoundConstructor public CoreStep(SimpleBuildStep delegate) {
        this.delegate = delegate;
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new Execution(delegate, context);
    }

    private static final class Execution extends SynchronousNonBlockingStepExecution<Void> {

        @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used when starting.")
        private transient final SimpleBuildStep delegate;

        Execution(SimpleBuildStep delegate, StepContext context) {
            super(context);
            this.delegate = delegate;
        }

        @Override protected Void run() throws Exception {
            FilePath workspace = getContext().get(FilePath.class);
            workspace.mkdirs();
            delegate.perform(getContext().get(Run.class), workspace, getContext().get(Launcher.class), getContext().get(TaskListener.class));
            return null;
        }

        @Override public String getStatus() {
            String supe = super.getStatus();
            return delegate != null ? delegate.getClass().getName() + ": " + supe : supe;
        }

        private static final long serialVersionUID = 1L;

    }

    @Extension public static final class DescriptorImpl extends StepDescriptor {

        @Override public String getFunctionName() {
            return "step";
        }

        @Override public String getDisplayName() {
            return "General Build Step";
        }

        @Override
        public boolean isMetaStep() {
            return true;
        }

        public Collection<? extends Descriptor<?>> getApplicableDescriptors() {
            // Jenkins.instance.getDescriptorList(SimpleBuildStep) is empty, presumably because that itself is not a Describable.
            List<Descriptor<?>> r = new ArrayList<>();
            populate(r, Builder.class);
            populate(r, Publisher.class);
            return r;
        }
        private <T extends Describable<T>,D extends Descriptor<T>> void populate(List<Descriptor<?>> r, Class<T> c) {
            for (Descriptor<?> d : Jenkins.getActiveInstance().getDescriptorList(c)) {
                if (SimpleBuildStep.class.isAssignableFrom(d.clazz)) {
                    r.add(d);
                }
            }
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, FilePath.class, Launcher.class, TaskListener.class);
        }

        @Override public String argumentsToString(Map<String, Object> namedArgs) {
            Map<String, Object> delegateArguments = delegateArguments(namedArgs.get("delegate"));
            return delegateArguments != null ? super.argumentsToString(delegateArguments) : null;
        }

        @SuppressWarnings("unchecked")
        static @CheckForNull Map<String, Object> delegateArguments(@CheckForNull Object delegate) {
            if (delegate instanceof UninstantiatedDescribable) {
                // TODO JENKINS-45101 getStepArgumentsAsString does not resolve its arguments
                // thus delegate.model == null and we cannot inspect DescribableModel.soleRequiredParameter
                // thus for, e.g., `junit testResults: '*.xml', keepLongStdio: true` we will get null
                return new HashMap<>(((UninstantiatedDescribable) delegate).getArguments());
            } else if (delegate instanceof Map) {
                Map<String, Object> r = new HashMap<>();
                r.putAll((Map) delegate);
                r.remove(DescribableModel.CLAZZ);
                return r;
            } else {
                return null;
            }
        }

    }

}
