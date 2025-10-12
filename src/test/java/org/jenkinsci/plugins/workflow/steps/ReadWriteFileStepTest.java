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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.Functions;
import hudson.model.TopLevelItem;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.NodeStepTypePredicate;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ReadWriteFileStepTest {

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void basics() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        boolean win = Functions.isWindows();
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" + (win ? "  bat 'echo hello > f1'\n" : "  sh 'echo hello > f1'\n")
                        + "  def text = readFile 'f1'\n"
                        + "  text = text.toUpperCase()\n"
                        + "  writeFile file: 'f2', text: text\n"
                        + (win ? "  bat 'type f2'\n" : "  sh 'cat f2'\n")
                        + "}",
                true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("HELLO", b);
        List<FlowNode> coreStepNodes =
                new DepthFirstScanner().filteredNodes(b.getExecution(), new NodeStepTypePredicate("writeFile"));
        assertThat(coreStepNodes, Matchers.hasSize(1));
        assertEquals("f2", ArgumentsAction.getStepArgumentsAsString(coreStepNodes.get(0)));
    }

    @Test
    void shouldTestFileExistsStep() throws Exception {
        final WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                        node {
                          echo "test.txt - FileExists: ${fileExists('test.txt')}"\s
                          writeFile file: 'test2.txt', text:'content of file'\s
                          echo "test2.txt - FileExists: ${fileExists('test2.txt')}"\s
                        }""",
                true));

        WorkflowRun run = p.scheduleBuild2(0).get();
        r.assertLogContains("test.txt - FileExists: false", run);
        r.assertLogContains("test2.txt - FileExists: true", run);
        r.assertBuildStatusSuccess(run);
    }

    @Issue(("JENKINS-27094"))
    @Test
    void readAndWriteFileUsesCorrectEncoding() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                        node {
                          def text = 'HELLO'
                          writeFile file: 'f1', text: text, encoding: 'utf-32le'
                          def text2 = readFile file: 'f1', encoding: 'utf-32le'
                          echo text2
                        }""",
                true));
        r.assertLogContains("HELLO", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

    @Issue(("JENKINS-27094"))
    @Test
    void testKnownCharsetRoundtrip() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                        node {
                          def text = 'HELLO'
                          writeFile file: 'f1', text: '¤', encoding: 'iso-8859-1'
                          def text2 = readFile file: 'f1', encoding: 'iso-8859-15'
                          echo text2
                        }""",
                true));
        r.assertLogContains("€", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

    @Issue("JENKINS-52313")
    @Test
    void testBinaryFileRoundtrip() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                        node {
                          semaphore 'file-created'
                          def utf8Text = readFile file: 'binary-file', encoding: 'UTF-8'
                          writeFile file: 'round-trip-utf8', text: utf8Text, encoding: 'UTF-8'
                          def base64Text = readFile file: 'binary-file', encoding: 'Base64'
                          writeFile file: 'round-trip-base64', text: base64Text, encoding: 'Base64'
                          semaphore 'bytes-checked'
                        }""",
                true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("file-created/1", b);
        byte[] bytes = {0x48, 0x45, 0x4c, 0x4c, 0x4f, (byte) 0x80, (byte) 0xec, (byte) 0xf4, 0x00, 0x0d, 0x1b};
        try (OutputStream stream =
                r.jenkins.getWorkspaceFor(p).child("binary-file").write()) {
            stream.write(bytes);
        }
        SemaphoreStep.success("file-created/1", null);
        SemaphoreStep.waitForStart("bytes-checked/1", b);
        assertThat(
                "The data should not round-trip correctly using UTF-8 encoding",
                getBytes(p, "round-trip-utf8"),
                not(equalTo(bytes)));
        assertThat(
                "The data should round-trip correctly using Base64 encoding",
                getBytes(p, "round-trip-base64"),
                equalTo(bytes));
        SemaphoreStep.success("bytes-checked/1", null);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
    }

    private byte[] getBytes(TopLevelItem item, String fileName) throws Exception {
        try (InputStream stream =
                r.jenkins.getWorkspaceFor(item).child(fileName).read()) {
            return IOUtils.toByteArray(stream);
        }
    }
}
