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

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import java.lang.reflect.Method;
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
            final StepContext ctx = this.getContext();
            final Run<?,?> run = ctx.get(Run.class);
            assert run != null;
            final TaskListener listener = ctx.get(TaskListener.class);
            assert listener != null;
            final EnvVars env = ctx.get(EnvVars.class);
            assert env != null;
            final FilePath workspace = ctx.get(FilePath.class);
            final Launcher launcher = ctx.get(Launcher.class);
            boolean workspaceRequired = true;
            // In Jenkins 2.25x, a SimpleBuildStep can indicate that it does not require a workspace context by
            // overriding a requiresWorkspace() method to return false. So use that if it's available.
            // Note: this uses getMethod() on the delegate's type and not SimpleBuildStep so that an implementation can
            // get this behaviour without switching to Jenkins 2.25x itself.
            // TODO: Use 'workspaceRequired = this.delegate.requiresWorkspace()' once this plugin depends on Jenkins 2.25x or later.
            try {
                final Method requiresWorkspace = this.delegate.getClass().getMethod("requiresWorkspace");
                workspaceRequired = (boolean) requiresWorkspace.invoke(this.delegate);
            } catch(NoSuchMethodException e) {
                // ok, default to true
            }
            if (workspaceRequired) {
                if (workspace == null) {
                    throw new MissingContextVariableException(FilePath.class);
                }
                if (launcher == null) {
                    throw new MissingContextVariableException(Launcher.class);
                }
            }
            if (workspace != null) {
                workspace.mkdirs();
            }
            // always pass the workspace context when available, even when it is not strictly required
            if (workspace != null && launcher != null) {
                // In Jenkins 2.241, a SimpleBuildStep has an overload of perform() that also takes an EnvVars. If that
                // is available, we use it.
                // Note: this uses getMethod() on the delegate's type and not SimpleBuildStep so that an implementation
                // can get this behaviour without switching to Jenkins 2.241 itself.
                // TODO: Use 'this.delegate.perform(run, workspace, env, launcher, listener)' once this plugin depends on Jenkins 2.241 or later.
                try {
                    final Method perform = this.delegate.getClass().getMethod("perform", Run.class, FilePath.class,
                            EnvVars.class, Launcher.class, TaskListener.class);
                    perform.invoke(this.delegate, run, workspace, env, launcher, listener);
                } catch (NoSuchMethodException e) {
                    this.delegate.perform(run, workspace, launcher, listener);
                }
            } else {
                // If we get here, workspaceRequired is false and there is no workspace context. In that case, the
                // overload of perform() introduced in Jenkins 2.25x MUST exist (so no try block here).
                // Note: this uses getMethod() on the delegate's type and not SimpleBuildStep so that an implementation
                // can get this behaviour without switching to Jenkins 2.25x itself.
                // TODO: Use 'this.delegate.perform(run, env, listener)' once the minimum core version for this plugin is 2.25x or newer.
                final Method perform = this.delegate.getClass().getMethod("perform", Run.class, EnvVars.class, TaskListener.class);
                perform.invoke(this.delegate, run, env, listener);
            }
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
            for (Descriptor<?> d : Jenkins.get().getDescriptorList(c)) {
                if (SimpleBuildStep.class.isAssignableFrom(d.clazz)) {
                    r.add(d);
                }
            }
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, EnvVars.class, TaskListener.class);
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
