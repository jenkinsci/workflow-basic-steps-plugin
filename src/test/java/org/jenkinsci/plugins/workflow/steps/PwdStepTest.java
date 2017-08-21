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

import hudson.FilePath;
import hudson.slaves.WorkspaceList;
import java.util.List;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.NodeStepTypePredicate;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

public class PwdStepTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void basics() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {echo \"cwd='${pwd()}'\"}", true));
        r.assertLogContains("cwd='" + r.jenkins.getWorkspaceFor(p) + "'", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

    // TODO use 1.652 use WorkspaceList.tempDir
    private static FilePath tempDir(FilePath ws) {
        return ws.sibling(ws.getName() + System.getProperty(WorkspaceList.class.getName(), "@") + "tmp");
    }

    @Test public void tmp() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {echo \"tmp='${pwd tmp: true}'\"}", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("tmp='" + tempDir(r.jenkins.getWorkspaceFor(p)) + "'", b);
        List<FlowNode> coreStepNodes = new DepthFirstScanner().filteredNodes(b.getExecution(), new NodeStepTypePredicate("pwd"));
        assertThat(coreStepNodes, Matchers.hasSize(1));
        assertEquals(null, ArgumentsAction.getStepArgumentsAsString(coreStepNodes.get(0)));
    }

}
