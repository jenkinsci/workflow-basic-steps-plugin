/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.support.steps.stash;

import java.io.File;
import java.util.List;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.StashManager;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.NodeStepTypePredicate;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

public class StashTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-26942")
    @Test public void smokes() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
            "node {\n" +
            "  writeFile file: 'subdir/fname', text: 'whatever'\n" +
            "  writeFile file: 'subdir/other', text: 'more'\n" +
            "  dir('subdir') {stash 'whatever'}\n" +
            "}\n" +
            "node {\n" +
            "  dir('elsewhere') {\n" +
            "    unstash 'whatever'\n" +
            "    echo \"got fname: ${readFile 'fname'} other: ${readFile 'other'}\"\n" +
            "  }\n" +
            "  writeFile file: 'at-top', text: 'ignored'\n" +
            "  stash name: 'from-top', includes: 'elsewhere/', excludes: '**/other'\n" +
            "  semaphore 'ending'\n" +
            "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("ending/1", b);
        assertEquals("{from-top={elsewhere/fname=whatever}, whatever={fname=whatever, other=more}}", StashManager.stashesOf(b).toString());
        SemaphoreStep.success("ending/1", null);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("got fname: whatever other: more", b);
        assertEquals("{}", StashManager.stashesOf(b).toString()); // TODO flake expected:<{[]}> but was:<{[from-top={elsewhere/fname=whatever}, whatever={fname=whatever, other=more}]}>
    }

    @Issue("JENKINS-31086")
    @Test public void testDefaultExcludes() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "  writeFile file: 'subdir/.gitignore', text: 'whatever'\n" +
                        "  writeFile file: 'subdir/otherfile', text: 'whatever'\n" +
                        "  dir('subdir') {stash name:'has-gitignore', useDefaultExcludes: false}\n" +
                        "  dir('subdir') {stash name:'no-gitignore' }\n" +
                        "  dir('first-unstash') {\n" +
                        "    unstash('has-gitignore')\n" +
                        "    echo \"gitignore exists? ${fileExists '.gitignore'}\"\n" +
                        "  }\n" +
                        "  dir('second-unstash') {\n" +
                        "    unstash('no-gitignore')\n" +
                        "    echo \"gitignore does not exist? ${fileExists '.gitignore'}\"\n" +
                        "  }\n" +
                        "}", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("gitignore exists? true", b);
        r.assertLogContains("gitignore does not exist? false", b);
    }

    @Issue("JENKINS-37327")
    @Test public void testAllowEmpty() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "  stash name: 'whatever', allowEmpty: true\n" +
                        "  semaphore 'ending'\n" +
                        "}\n"
                        , true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("ending/1", b);
        assertEquals("{whatever={}}", StashManager.stashesOf(b).toString());
        SemaphoreStep.success("ending/1", null);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("Stashed 0 file(s)", b);
        assertEquals("{}", StashManager.stashesOf(b).toString());
        List<FlowNode> coreStepNodes = new DepthFirstScanner().filteredNodes(b.getExecution(), new NodeStepTypePredicate("stash"));
        assertThat(coreStepNodes, Matchers.hasSize(1));
        assertEquals("whatever", ArgumentsAction.getStepArgumentsAsString(coreStepNodes.get(0)));
    }

    @Issue("JENKINS-40912")
    @Test public void fileList() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "  writeFile file: 'subdir/fname', text: 'whatever'\n" +
                        "  writeFile file: 'subdir/other', text: 'more'\n" +
                        "  dir('subdir') {\n" +
                        "    def l = stash 'whatever'\n" +
                        "    assert l.size() == 2\n" +
                        "    assert l.contains('fname')\n" +
                        "    assert l.contains('other')\n" +
                        "  }\n" +
                        "}\n" +
                        "node {\n" +
                        "  dir('elsewhere') {\n" +
                        "    def l2 = unstash 'whatever'\n" +
                        "    echo \"got fname: ${readFile 'fname'} other: ${readFile 'other'}\"\n" +
                        "    assert l2.size() == 2\n" +
                        "    assert l2.contains('fname')\n" +
                        "    assert l2.contains('other')\n" +
                        "  }\n" +
                        "  writeFile file: 'at-top', text: 'ignored'\n" +
                        "  def l3 = stash name: 'from-top', includes: 'elsewhere/', excludes: '**/other'\n" +
                        "  assert l3.size() == 1\n" +
                        // Note - ideally we'd be using l3.contains but that gets weird with file separators on Windows for some reason
                        "  def l3Name = l3[0]\n" +
                        "  assert l3Name.startsWith('elsewhere')\n" +
                        "  assert l3Name.endsWith('fname')\n" +
                        "  semaphore 'ending'\n" +
                        "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("ending/1", b);
        assertEquals("{from-top={elsewhere/fname=whatever}, whatever={fname=whatever, other=more}}", StashManager.stashesOf(b).toString());
        SemaphoreStep.success("ending/1", null);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("got fname: whatever other: more", b);
        assertEquals("{}", StashManager.stashesOf(b).toString()); // TODO flake expected:<{[]}> but was:<{[from-top={elsewhere/fname=whatever}, whatever={fname=whatever, other=more}]}>
    }

}
