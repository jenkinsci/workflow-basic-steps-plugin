package org.jenkinsci.plugins.workflow.steps;

import hudson.Extension;
import hudson.Launcher;
import hudson.Platform;
import jenkins.security.MasterToSlaveCallable;

import java.util.Collections;
import java.util.Set;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Checks whether we are running on Mac.
 */
public class IsMacStep extends Step {

    @DataBoundConstructor public IsMacStep() {}

    @Override public StepExecution start(StepContext context) throws Exception {
        return new Execution(context);
    }

    public static class Execution extends SynchronousStepExecution<Boolean> {

        Execution(StepContext context) {
            super(context);
        }

        @Override protected Boolean run() throws Exception {
            return getContext().get(Launcher.class).getChannel().call(new IsMacOS());
        }

        private static final long serialVersionUID = 1L;

    }

    @Extension public static final class DescriptorImpl extends StepDescriptor {

        @Override public String getFunctionName() {
            return "isMac";
        }

        @Override public String getDisplayName() {
            return "Checks if running on a Mac node";
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(Launcher.class);
        }

    }

    private static final class IsMacOS extends MasterToSlaveCallable<Boolean, Exception> {
        public Boolean call() {
            return Platform.isDarwin();
        }
        private static final long serialVersionUID = 1L;
    }

}
