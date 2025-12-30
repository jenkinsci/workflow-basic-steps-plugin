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

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.TimeUnit;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class StashTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    @Issue("JENKINS-26942")
    @Test
    void smokes() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("""
                        node {
                          writeFile file: 'subdir/fname', text: 'whatever'
                          writeFile file: 'subdir/other', text: 'more'
                          dir('subdir') {stash 'whatever'}
                        }
                        node {
                          dir('elsewhere') {
                            unstash 'whatever'
                            echo "got fname: ${readFile 'fname'} other: ${readFile 'other'}"
                          }
                          writeFile file: 'at-top', text: 'ignored'
                          stash name: 'from-top', includes: 'elsewhere/', excludes: '**/other'
                          semaphore 'ending'
                        }""", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("ending/1", b);
        assertEquals(
                "{from-top={elsewhere/fname=whatever}, whatever={fname=whatever, other=more}}",
                StashManager.stashesOf(b).toString());
        SemaphoreStep.success("ending/1", null);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("got fname: whatever other: more", b);
        await().timeout(5, TimeUnit.SECONDS)
                .until(() -> StashManager.stashesOf(b).isEmpty());
    }

    @Issue("JENKINS-31086")
    @Test
    void testDefaultExcludes() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("""
                        node {
                          writeFile file: 'subdir/.gitignore', text: 'whatever'
                          writeFile file: 'subdir/otherfile', text: 'whatever'
                          dir('subdir') {stash name:'has-gitignore', useDefaultExcludes: false}
                          dir('subdir') {stash name:'no-gitignore' }
                          dir('first-unstash') {
                            unstash('has-gitignore')
                            echo "gitignore exists? ${fileExists '.gitignore'}"
                          }
                          dir('second-unstash') {
                            unstash('no-gitignore')
                            echo "gitignore does not exist? ${fileExists '.gitignore'}"
                          }
                        }""", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("gitignore exists? true", b);
        r.assertLogContains("gitignore does not exist? false", b);
    }

    @Issue("JENKINS-37327")
    @Test
    void testAllowEmpty() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("""
                        node {
                          stash name: 'whatever', allowEmpty: true
                          semaphore 'ending'
                        }
                        """, true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("ending/1", b);
        assertEquals("{whatever={}}", StashManager.stashesOf(b).toString());
        SemaphoreStep.success("ending/1", null);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("Stashed 0 file(s)", b);
        await().timeout(5, TimeUnit.SECONDS)
                .until(() -> StashManager.stashesOf(b).isEmpty());
        List<FlowNode> coreStepNodes =
                new DepthFirstScanner().filteredNodes(b.getExecution(), new NodeStepTypePredicate("stash"));
        assertThat(coreStepNodes, Matchers.hasSize(1));
        assertEquals("whatever", ArgumentsAction.getStepArgumentsAsString(coreStepNodes.get(0)));
    }
}
