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

import hudson.model.Result;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.Fingerprinter;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.tasks.junit.TestResultAction;
import java.util.List;
import javax.mail.internet.InternetAddress;
import jenkins.plugins.mailer.tasks.i18n.Messages;
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
import org.jvnet.mock_javamail.Mailbox;

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

    @Test public void junitResultArchiver() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                  "node {\n"
                + "    writeFile text: '''<testsuite name='a'><testcase name='a1'/><testcase name='a2'><error>a2 failed</error></testcase></testsuite>''', file: 'a.xml'\n"
                + "    writeFile text: '''<testsuite name='b'><testcase name='b1'/><testcase name='b2'/></testsuite>''', file: 'b.xml'\n"
                + "    junit '*.xml'\n"
                + "}", true));
        WorkflowRun b = r.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0).get());
        TestResultAction a = b.getAction(TestResultAction.class);
        assertNotNull(a);
        assertEquals(4, a.getTotalCount());
        assertEquals(1, a.getFailCount());
        List<FlowNode> coreStepNodes = new DepthFirstScanner().filteredNodes(b.getExecution(), new NodeStepTypePredicate("step"));
        assertThat(coreStepNodes, Matchers.hasSize(1));
        assertEquals("*.xml", ArgumentsAction.getStepArgumentsAsString(coreStepNodes.get(0)));
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
        JUnitResultArchiver aa = new JUnitResultArchiver("target/surefire/*.xml");
        aa.setAllowEmptyResults(true);
        st.assertRoundTrip(new CoreStep(aa), "junit allowEmptyResults: true, testResults: 'target/surefire/*.xml'");
    }

    @Test public void coreStepWithSymbolWithSoleArg() throws Exception {
        JUnitResultArchiver aa = new JUnitResultArchiver("target/surefire/*.xml");
        st.assertRoundTrip(new CoreStep(aa), "junit 'target/surefire/*.xml'");
    }

}
