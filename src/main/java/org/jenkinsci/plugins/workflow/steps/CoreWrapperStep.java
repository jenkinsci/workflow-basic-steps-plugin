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
import java.lang.reflect.Method;
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
            SimpleBuildWrapper.Context c = null;
            // In Jenkins 2.25x, a createContext() method on SimpleBuildWrapper is required to ensure that a Disposer
            // registered on that context inherits the wrapper's workspace requirement. Use it when available.
            // TODO: Use 'c = this.delegate.createContext()' once this plugin depends on Jenkins 2.25x or later.
            try {
                final Method createContext = SimpleBuildWrapper.class.getMethod("createContext");
                c = (SimpleBuildWrapper.Context) createContext.invoke(this.delegate);
            }
            catch (NoSuchMethodException e) {
                c = new SimpleBuildWrapper.Context();
            }
            final StepContext context = getContext();
            final Run<?, ?> run = context.get(Run.class);
            assert run != null;
            {
                final TaskListener listener = context.get(TaskListener.class);
                assert listener != null;
                final EnvVars env = context.get(EnvVars.class);
                assert env != null;
                final FilePath workspace = context.get(FilePath.class);
                final Launcher launcher = context.get(Launcher.class);
                boolean workspaceRequired = true;
                // In Jenkins 2.25x, a SimpleBuildWrapper can indicate that it does not require a workspace context by
                // overriding a requiresWorkspace() method to return false. So use that if it's available.
                // Note: this uses getMethod() on the delegate's type and not SimpleBuildWrapper so that an
                // implementation can get this behaviour without switching to Jenkins 2.25x itself.
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
                // always pass the workspace context when available, even when it is not strictly required
                if (workspace != null && launcher != null) {
                    this.delegate.setUp(c, run, workspace, launcher, listener, env);
                } else {
                    // If we get here, workspaceRequired is false and there is no workspace context. In that case, the
                    // overload of setUp() introduced in Jenkins 2.25x MUST exist (so no try block here).
                    // Note: this uses getMethod() on the delegate's type and not SimpleBuildWrapper so that an
                    // implementation can get this behaviour without switching to Jenkins 2.25x itself.
                    // TODO: Use 'this.delegate.setUp(c, run, listener, env)' once the minimum core version for this plugin is 2.25x or newer.
                    final Method perform = this.delegate.getClass().getMethod("setUp", SimpleBuildWrapper.Context.class, Run.class, TaskListener.class, EnvVars.class);
                    perform.invoke(this.delegate, c, run, listener, env);
                }
            }
            BodyInvoker bodyInvoker = context.newBodyInvoker();
            Map<String,String> overrides = c.getEnv();
            if (!overrides.isEmpty()) {
                bodyInvoker.withContext(EnvironmentExpander.merge(context.get(EnvironmentExpander.class), new ExpanderImpl(overrides)));
            }
            ConsoleLogFilter filter = delegate.createLoggerDecorator(run);
            if (filter != null) {
                bodyInvoker.withContext(BodyInvoker.mergeConsoleLogFilters(context.get(ConsoleLogFilter.class), filter));
            }
            SimpleBuildWrapper.Disposer disposer = c.getDisposer();
            bodyInvoker.withCallback(disposer != null ? new Callback2(disposer) : BodyExecutionCallback.wrap(context)).start();
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

        @Override protected void finished(StepContext context) throws Exception {
            final Run<?,?> run = context.get(Run.class);
            assert run != null;
            final TaskListener listener = context.get(TaskListener.class);
            assert listener != null;
            final FilePath workspace = context.get(FilePath.class);
            final Launcher launcher = context.get(Launcher.class);
            boolean workspaceRequired = true;
            // In Jenkins 2.25x, a Disposer has a final requiresWorkspace() method that indicates whether or not it (or
            // more accurately, its associated wrapper) requires a workspace context. This is set up via the Context, as
            // long as that is created via SimpleBuildWrapper.createContext().
            // Note: this uses getMethod() on the disposer's type and not SimpleBuildWrapper.Disposer so that an
            // implementation can get this behaviour without switching to Jenkins 2.25x itself.
            // TODO: Use 'workspaceRequired = this.disposer.requiresWorkspace()' once this plugin depends on Jenkins 2.25x or later.
            try {
                final Method requiresWorkspace = this.disposer.getClass().getMethod("requiresWorkspace");
                workspaceRequired = (boolean) requiresWorkspace.invoke(this.disposer);
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
            // always pass the workspace context when available, even when it is not strictly required
            if (workspace != null && launcher != null) {
                this.disposer.tearDown(run, workspace, launcher, listener);
            } else {
                // If we get here, workspaceRequired is false and there is no workspace context. In that case, the
                // overload of tearDown() introduced in Jenkins 2.25x MUST exist (so no try block here).
                // Note: this uses getMethod() on the disposer's type and not SimpleBuildWrapper.Disposer so that an
                // implementation can get this behaviour without switching to Jenkins 2.25x itself.
                // TODO: Use 'this.disposer.tearDown(run, listener)' once the minimum core version for this plugin is 2.25x or newer.
                final Method perform = this.disposer.getClass().getMethod("tearDown", Run.class, TaskListener.class);
                perform.invoke(this.disposer, run, listener);
            }
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
