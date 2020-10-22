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

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Fingerprinter;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nonnull;
import javax.mail.internet.InternetAddress;
import jenkins.plugins.mailer.tasks.i18n.Messages;
import jenkins.tasks.SimpleBuildStep;
import org.hamcrest.Matchers;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.NodeStepTypePredicate;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.mock_javamail.Mailbox;
import org.kohsuke.stapler.DataBoundConstructor;

public class CoreStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    private SnippetizerTester st = new SnippetizerTester(r);

    @Test public void artifactArchiver() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {writeFile text: '', file: 'x.txt'; archiveArtifacts artifacts: 'x.txt', fingerprint: true}", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        List<WorkflowRun.Artifact> artifacts = b.getArtifacts();
        assertEquals(1, artifacts.size());
        assertEquals("x.txt", artifacts.get(0).relativePath);
        Fingerprinter.FingerprintAction fa = b.getAction(Fingerprinter.FingerprintAction.class);
        assertNotNull(fa);
        assertEquals("[x.txt]", fa.getRecords().keySet().toString());
    }

    @Issue("JENKINS-31931")
    @Test public void artifactArchiverNonexistent() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {archiveArtifacts artifacts: 'nonexistent/', allowEmptyArchive: true}", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        p.setDefinition(new CpsFlowDefinition("node {archiveArtifacts 'nonexistent/'}", true));
        b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        /* TODO bug in ArtifactArchiver:
        r.assertLogContains(hudson.tasks.Messages.ArtifactArchiver_NoMatchFound("nonexistent/"), b);
        */
    }

    @Test public void fingerprinter() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {writeFile text: '', file: 'x.txt'; fingerprint 'x.txt'}", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        Fingerprinter.FingerprintAction fa = b.getAction(Fingerprinter.FingerprintAction.class);
        assertNotNull(fa);
        assertEquals("[x.txt]", fa.getRecords().keySet().toString());
    }

    @Test public void javadoc() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                  "node {\n"
                + "    writeFile text: 'hello world', file: 'docs/index.html'\n"
                + "    step([$class: 'JavadocArchiver', javadocDir: 'docs'])\n"
                + "}", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals("hello world", r.createWebClient().getPage(p, "javadoc/").getWebResponse().getContentAsString());
        List<FlowNode> coreStepNodes = new DepthFirstScanner().filteredNodes(b.getExecution(), new NodeStepTypePredicate("step"));
        assertThat(coreStepNodes, Matchers.hasSize(1));
        assertEquals("docs", ArgumentsAction.getStepArgumentsAsString(coreStepNodes.get(0)));
    }

    @Test public void mailer() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        String recipient = "test@nowhere.net";
        p.setDefinition(new CpsFlowDefinition(
                  "node {\n"
                + "    writeFile text: '''<testsuite name='s'><testcase name='c'><error>failed</error></testcase></testsuite>''', file: 'r.xml'\n"
                + "    junit 'r.xml'\n"
                + "    step([$class: 'Mailer', recipients: '" + recipient + "'])\n"
                + "}", true));
        Mailbox inbox = Mailbox.get(new InternetAddress(recipient));
        inbox.clear();
        WorkflowRun b = r.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0).get());
        assertEquals(1, inbox.size());
        assertEquals(/* MailSender.createUnstableMail/getSubject */Messages.MailSender_UnstableMail_Subject() + " " + b.getFullDisplayName(), inbox.get(0).getSubject());
        p.setDefinition(new CpsFlowDefinition(
                  "node {\n"
                + "    catchError {error 'oops'}\n"
                + "    step([$class: 'Mailer', recipients: '" + recipient + "'])\n"
                + "}", true));
        inbox.clear();
        b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        assertEquals(1, inbox.size());
        assertEquals(Messages.MailSender_FailureMail_Subject() + " " + b.getFullDisplayName(), inbox.get(0).getSubject());
        p.setDefinition(new CpsFlowDefinition(
                  "node {\n"
                + "    catchError {echo 'ok'}\n"
                + "    step([$class: 'Mailer', recipients: '" + recipient + "'])\n"
                + "}", true));
        inbox.clear();
        b = r.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0).get());
        assertEquals(0, inbox.size());
        p.setDefinition(new CpsFlowDefinition(
                  "node {\n"
                + "    try {error 'oops'} catch (e) {echo \"caught ${e}\"; currentBuild.result = 'FAILURE'}\n"
                + "    step([$class: 'Mailer', recipients: '" + recipient + "'])\n"
                + "}", true));
        inbox.clear();
        b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        assertEquals(1, inbox.size());
        assertEquals(Messages.MailSender_FailureMail_Subject() + " " + b.getFullDisplayName(), inbox.get(0).getSubject());
    }

    @Test public void coreStepWithSymbol() throws Exception {
        ArtifactArchiver aa = new ArtifactArchiver("some-artifacts");
        aa.setFingerprint(true);
        st.assertRoundTrip(new CoreStep(aa), "archiveArtifacts artifacts: 'some-artifacts', fingerprint: true");
    }

    @Test public void coreStepWithSymbolWithSoleArg() throws Exception {
        ArtifactArchiver aa = new ArtifactArchiver("some-artifacts");
        st.assertRoundTrip(new CoreStep(aa), "archiveArtifacts 'some-artifacts'");
    }

    public static class BuilderWithEnvironment extends Builder implements SimpleBuildStep {

        @DataBoundConstructor
        public BuilderWithEnvironment() {
        }

        @Override
        public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull EnvVars env, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
            assertNull(env.get("BUILD_ID"));
            assertEquals("JENKINS-29144", env.get("TICKET"));
        }

        @Override
        public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
            fail("This method should not get called.");
        }

        @Symbol("buildWithEnvironment")
        @TestExtension("builderWithEnvironment")
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }
        }
    }

    @Issue("JENKINS-29144")
    @Test
    public void builderWithEnvironment() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node { withEnv(['TICKET=JENKINS-29144', 'BUILD_ID=']) { buildWithEnvironment() } }", true));
        r.buildAndAssertSuccess(p);
    }

    public static class BuilderWithWorkspaceRequirement extends Builder implements SimpleBuildStep {
        @DataBoundConstructor
        public BuilderWithWorkspaceRequirement() {
        }

        @Override public void perform(@Nonnull Run<?, ?> run, @Nonnull EnvVars env, @Nonnull TaskListener listener) throws InterruptedException, IOException {
            listener.getLogger().println("workspace context required, but not provided!");
        }
        @Override public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull EnvVars env, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
            listener.getLogger().println("workspace context required and provided.");
        }
        // While the @TextExtension supposedly limits this descriptor to the named test method, it still gets picked up
        // via the @Symbol from anywhere. So that needs to be unique across tests.
        @Symbol("builderWithWorkspaceRequirement")
        @TestExtension("builderWithWorkspaceRequirement")
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }
        }
    }

    @Issue("JENKINS-46175")
    @Test
    public void builderWithWorkspaceRequirement() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        // make sure it runs inside node
        p.setDefinition(new CpsFlowDefinition("node { builderWithWorkspaceRequirement() }", true));
        r.assertLogContains("workspace context required and provided.", r.buildAndAssertSuccess(p));
        // and fails outside of one
        p.setDefinition(new CpsFlowDefinition("builderWithWorkspaceRequirement()", true));
        r.assertLogContains(MissingContextVariableException.class.getCanonicalName(), r.buildAndAssertStatus(Result.FAILURE, p));
    }

    public static class BuilderWithoutWorkspaceRequirement extends Builder implements SimpleBuildStep {
        @DataBoundConstructor
        public BuilderWithoutWorkspaceRequirement() {
        }

        @Override public void perform(@Nonnull Run<?, ?> run, @Nonnull EnvVars env, @Nonnull TaskListener listener) throws InterruptedException, IOException {
            listener.getLogger().println("workspace context not needed.");
        }

        @Override public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull EnvVars env, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
            listener.getLogger().println("workspace context not needed, but provided.");
        }

        @Override public boolean requiresWorkspace() {
            return false;
        }
        // While the @TextExtension supposedly limits this descriptor to the named test method, it still gets picked up
        // via the @Symbol from anywhere. So that needs to be unique across tests.
        @Symbol("builderWithoutWorkspaceRequirement")
        @TestExtension("builderWithoutWorkspaceRequirement")
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }
        }
    }

    @Issue("JENKINS-46175")
    @Test
    public void builderWithoutWorkspaceRequirement() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        // make sure it runs outside of node
        p.setDefinition(new CpsFlowDefinition("builderWithoutWorkspaceRequirement()", true));
        r.assertLogContains("workspace context not needed.", r.buildAndAssertSuccess(p));
        // but also inside it
        p.setDefinition(new CpsFlowDefinition("node { builderWithoutWorkspaceRequirement() }", true));
        r.assertLogContains("workspace context not needed, but provided.", r.buildAndAssertSuccess(p));
    }

}
