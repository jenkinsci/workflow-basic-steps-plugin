/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildWrapper;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A step that runs a {@link SimpleBuildWrapper} as defined in Jenkins core.
 */
public class CoreWrapperStep extends Step {

    private final SimpleBuildWrapper delegate;

    @DataBoundConstructor public CoreWrapperStep(SimpleBuildWrapper delegate) {
        this.delegate = delegate;
    }

    public SimpleBuildWrapper getDelegate() {
        return delegate;
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new Execution2(delegate, context);
    }

    /** @deprecated Only here for serial compatibility. */
    @Deprecated
    public static final class Execution extends AbstractStepExecutionImpl {

        private static final long serialVersionUID = 1;

        @Override public boolean start() throws Exception {
            throw new AssertionError();
        }

    }

    private static final class Execution2 extends GeneralNonBlockingStepExecution {

        private static final long serialVersionUID = 1;

        private transient final SimpleBuildWrapper delegate;

        Execution2(SimpleBuildWrapper delegate, StepContext context) {
            super(context);
            this.delegate = delegate;
        }

        @Override public boolean start() throws Exception {
            run(this::doStart);
            return false;
        }

        private void doStart() throws Exception {
            // Get the context objects that may be required
            final StepContext context = this.getContext();
            final Run<?,?> run = context.get(Run.class);
            final FilePath workspace = context.get(FilePath.class);
            final Launcher launcher = context.get(Launcher.class);
            final TaskListener listener = context.get(TaskListener.class);
            final EnvVars env = context.get(EnvVars.class);
            // context.get() guarantees these, but let code inspections know it
            assert run != null;
            assert listener != null;
            assert env != null;
            // Check the ones that are optionally required
            if (this.delegate.requiresWorkspace() && workspace == null) {
                throw new MissingContextVariableException(FilePath.class);
            }
            if (this.delegate.requiresLauncher() && launcher == null) {
                throw new MissingContextVariableException(Launcher.class);
            }
            // Set it up
            final SimpleBuildWrapper.Context c = new SimpleBuildWrapper.Context();
            delegate.setUp(c, run, workspace, launcher, listener, env);
            BodyInvoker bodyInvoker = getContext().newBodyInvoker();
            Map<String,String> overrides = c.getEnv();
            if (!overrides.isEmpty()) {
                bodyInvoker.withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new ExpanderImpl(overrides)));
            }
            ConsoleLogFilter filter = delegate.createLoggerDecorator(run);
            if (filter != null) {
                bodyInvoker.withContext(BodyInvoker.mergeConsoleLogFilters(getContext().get(ConsoleLogFilter.class), filter));
            }
            SimpleBuildWrapper.Disposer disposer = c.getDisposer();
            bodyInvoker.withCallback(disposer != null ? new Callback2(disposer) : BodyExecutionCallback.wrap(getContext())).start();
        }

        private final class Callback2 extends TailCall {

            private static final long serialVersionUID = 1;

            private final @Nonnull SimpleBuildWrapper.Disposer disposer;

            Callback2(SimpleBuildWrapper.Disposer disposer) {
                this.disposer = disposer;
            }

            @Override protected void finished(StepContext context) throws Exception {
                new Callback(disposer).finished(context);
            }

        }

    }

    private static final class ExpanderImpl extends EnvironmentExpander {
        private static final long serialVersionUID = 1;
        private final Map<String,String> overrides;
        ExpanderImpl(Map<String,String> overrides) {
            this.overrides = /* ensure serializability*/ new HashMap<>(overrides);
        }
        @Override public void expand(EnvVars env) throws IOException, InterruptedException {
            // Distinct from EnvironmentExpander.constant since we are also expanding variables.
            env.overrideExpandingAll(overrides);
        }
    }

    private static final class Callback extends BodyExecutionCallback.TailCall {

        private static final long serialVersionUID = 1;

        private final @Nonnull SimpleBuildWrapper.Disposer disposer;

        Callback(@Nonnull SimpleBuildWrapper.Disposer disposer) {
            this.disposer = disposer;
        }

        @Override protected void finished(@Nonnull StepContext context) throws Exception {
            // Get the context objects that may be required
            final Run<?,?> run = context.get(Run.class);
            final FilePath workspace = context.get(FilePath.class);
            final Launcher launcher = context.get(Launcher.class);
            final TaskListener listener = context.get(TaskListener.class);
            // context.get() guarantees these, but let code inspections know it
            assert run != null;
            assert listener != null;
            // Tear it down
            disposer.tearDown(run, workspace, launcher, listener);
        }

    }

    @Extension public static final class DescriptorImpl extends StepDescriptor {

        @Override public String getFunctionName() {
            return "wrap";
        }

        @Override public String getDisplayName() {
            return "General Build Wrapper";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override public boolean isMetaStep() {
            return true;
        }

        // getPropertyType("delegate").getApplicableDescriptors() does not work, because extension lists do not work on subtypes.
        public Collection<BuildWrapperDescriptor> getApplicableDescriptors() {
            Collection<BuildWrapperDescriptor> r = new ArrayList<>();
            for (BuildWrapperDescriptor d : Jenkins.get().getExtensionList(BuildWrapperDescriptor.class)) {
                if (SimpleBuildWrapper.class.isAssignableFrom(d.clazz)) {
                    r.add(d);
                }
            }
            return r;
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, TaskListener.class, EnvVars.class);
        }

        @Override public String argumentsToString(Map<String, Object> namedArgs) {
            Map<String, Object> delegateArguments = CoreStep.DescriptorImpl.delegateArguments(namedArgs.get("delegate"));
            return delegateArguments != null ? super.argumentsToString(delegateArguments) : null;
        }

    }

}
