package org.jenkinsci.plugins.workflow.steps;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import jenkins.model.ArtifactManagerConfiguration;
import jenkins.util.VirtualFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.DirectArtifactManagerFactory;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ArtifactArchiverStepTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();
    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    /**
     * Archive and unarchive file
     */
    @Test
    void archive() throws Exception {
        // job setup
        WorkflowJob foo = r.jenkins.createProject(WorkflowJob.class, "foo");
        foo.setDefinition(new CpsFlowDefinition(StringUtils.join(Arrays.asList(
                "node {",
                "  writeFile text: 'hello world', file: 'msg'",
                "  archive 'm*'",
                "  unarchive(mapping:['msg':'msg.out'])",
                "  archive 'msg.out'",
                "}"), "\n"), true));


        // get the build going, and wait until workflow pauses
        WorkflowRun b = r.assertBuildStatusSuccess(foo.scheduleBuild2(0).get());

        VirtualFile archivedFile = b.getArtifactManager().root().child("msg.out");
        assertTrue(archivedFile.exists());
        try (InputStream stream = archivedFile.open()) {
            assertEquals("hello world", IOUtils.toString(stream, StandardCharsets.UTF_8));
        }
        r.assertLogContains(Messages.ArtifactArchiverStepExecution_Deprecated(), b);
    }

    @Issue("JENKINS-31931")
    @Test
    void nonexistent() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {archive 'nonexistent/'}", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.assertLogContains(Messages.ArtifactArchiverStepExecution_NoFiles("nonexistent/"), b);

        p.setDefinition(new CpsFlowDefinition("node { archive includes:'nonexistent/', excludes:'pants' }", true));
        WorkflowRun b2 = r.buildAndAssertSuccess(p);
        r.assertLogContains(Messages.ArtifactArchiverStepExecution_NoFilesWithExcludes("nonexistent/", "pants"), b2);
    }

    @Test
    void unarchiveDir() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(StringUtils.join(Arrays.asList(
                "node {",
                "  writeFile text: 'one', file: 'a/1'; writeFile text: 'two', file: 'a/b/2'",
                "  archive 'a/'",
                "  dir('new') {",
                "    unarchive mapping: ['a/' : '.']",
                "    echo \"${readFile 'a/1'}/${readFile 'a/b/2'}\"",
                "  }",
                "}"), "\n"), true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0).get());
        VirtualFile archivedFile = b.getArtifactManager().root().child("a/b/2");
        assertTrue(archivedFile.exists());
        try (InputStream stream = archivedFile.open()) {
            assertEquals("two", IOUtils.toString(stream, StandardCharsets.UTF_8));
        }
        r.assertLogContains("one/two", b);
    }

    @Issue("JENKINS-49635")
    @Test
    void directDownload() throws Exception {
        ArtifactManagerConfiguration.get().getArtifactManagerFactories().add(new DirectArtifactManagerFactory());
        r.createSlave("remote1", null, null);
        r.createSlave("remote2", null, null);
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node('remote1') {writeFile file: 'x', text: 'contents'; archiveArtifacts 'x'}; node('remote2') {unarchive mapping: [x: 'x']; echo(/loaded ${readFile('x')}/)}", true));
        DirectArtifactManagerFactory.whileBlockingOpen(() -> {
            r.assertLogContains("loaded contents", r.buildAndAssertSuccess(p));
            return null;
        });
    }

}
