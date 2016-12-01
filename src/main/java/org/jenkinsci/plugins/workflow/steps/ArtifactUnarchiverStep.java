package org.jenkinsci.plugins.workflow.steps;

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Map;
import java.util.Set;

/**
 * @author Kohsuke Kawaguchi
 */
public class ArtifactUnarchiverStep extends Step {
    /**
     * Files to copy over.
     */
    @DataBoundSetter public Map<String,String> mapping;

    // TBD: alternate single-file option value ~ Collections.singletonMap(value, value)

    @DataBoundConstructor public ArtifactUnarchiverStep() {}

    @Override public StepExecution start(StepContext context) throws Exception {
        return new ArtifactUnarchiverStepExecution(mapping, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "unarchive";
        }

        @Override
        public String getDisplayName() {
            return "Copy archived artifacts into the workspace";
        }

        @Override public boolean isAdvanced() {
            return true;
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(FilePath.class, Run.class);
        }

    }

}
