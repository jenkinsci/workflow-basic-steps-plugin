package org.jenkinsci.plugins.workflow.steps;

import hudson.Extension;
import hudson.Util;
import hudson.tasks.ArtifactArchiver;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Artifact archiving.
 *
 * @author Kohsuke Kawaguchi
 */
public class ArtifactArchiverStep extends AbstractStepImpl {

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

    @DataBoundSetter public void setExcludes(String excludes) {
        this.excludes = Util.fixEmptyAndTrim(excludes);
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(ArtifactArchiverStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "archive";
        }

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
            return ArtifactArchiver.DescriptorImpl.class.isAnnotationPresent(Symbol.class);
        }

    }
}
