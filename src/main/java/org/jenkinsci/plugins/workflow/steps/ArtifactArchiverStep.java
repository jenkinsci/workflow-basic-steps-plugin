package org.jenkinsci.plugins.workflow.steps;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Artifact archiving.
 *
 * @author Kohsuke Kawaguchi
 */
public class ArtifactArchiverStep extends Step {

    private final String includes;
    private String excludes;

    @DataBoundConstructor
    public ArtifactArchiverStep(String includes) {
        this.includes = includes;
    }

    public String getIncludes() {
        return includes;
    }

    public String getExcludes() {
        return excludes;
    }

    @DataBoundSetter
    public void setExcludes(String excludes) {
        this.excludes = Util.fixEmptyAndTrim(excludes);
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new ArtifactArchiverStepExecution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "archive";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Archive artifacts";
        }

        /**
         * When the {@code archiveArtifacts} symbol is available, {@link CoreStep} may be used instead,
         * with no more verbose a syntax but more configuration options.
         */
        @Override
        public boolean isAdvanced() {
            return true;
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            Set<Class<?>> context = new HashSet<>();
            Collections.addAll(context, FilePath.class, Run.class, Launcher.class, TaskListener.class);
            return Collections.unmodifiableSet(context);
        }
    }
}
