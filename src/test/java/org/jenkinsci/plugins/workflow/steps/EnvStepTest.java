/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import hudson.Functions;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.NodeStepTypePredicate;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class EnvStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test public void overriding() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    "env.CUSTOM = 'initial'\n" +
                    "env.FOOPATH = node {isUnix() ? '/opt/foos' : 'C:\\\\foos'}\n" +
                    "env.NULLED = 'outside'\n" +
                    "node {\n" +
                    "  withEnv(['CUSTOM=override', 'NOVEL=val', 'BUILD_TAG=custom', 'NULLED=', isUnix() ? 'FOOPATH+BALL=/opt/ball' : 'FOOPATH+BALL=C:\\\\ball']) {\n" +
                    "    isUnix() ? sh('echo inside CUSTOM=$CUSTOM NOVEL=$NOVEL BUILD_TAG=$BUILD_TAG NULLED=$NULLED FOOPATH=$FOOPATH:') : bat('echo inside CUSTOM=%CUSTOM% NOVEL=%NOVEL% BUILD_TAG=%BUILD_TAG% NULLED=%NULLED% FOOPATH=%FOOPATH%;')\n" +
                    "    echo \"groovy NULLED=${env.NULLED}\"\n" +
                    "  }\n" +
                    "  isUnix() ? sh('echo outside CUSTOM=$CUSTOM NOVEL=$NOVEL NULLED=outside') : bat('echo outside CUSTOM=%CUSTOM% NOVEL=%NOVEL% NULLED=outside')\n" +
                    "}", true));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains(Functions.isWindows() ? "inside CUSTOM=override NOVEL=val BUILD_TAG=custom NULLED= FOOPATH=C:\\ball;C:\\foos;" : "inside CUSTOM=override NOVEL=val BUILD_TAG=custom NULLED= FOOPATH=/opt/ball:/opt/foos:", b);
                story.j.assertLogContains("groovy NULLED=null", b);
                story.j.assertLogContains("outside CUSTOM=initial NOVEL= NULLED=outside", b);
                List<FlowNode> coreStepNodes = new DepthFirstScanner().filteredNodes(b.getExecution(), Predicates.and(new NodeStepTypePredicate("withEnv"), new Predicate<FlowNode>() {
                    @Override public boolean apply(FlowNode n) {
                        return n instanceof StepStartNode && !((StepStartNode) n).isBody();
                    }
                }));
                assertThat(coreStepNodes, Matchers.hasSize(1));
                assertEquals("CUSTOM, NOVEL, BUILD_TAG, NULLED, FOOPATH+BALL", ArgumentsAction.getStepArgumentsAsString(coreStepNodes.get(0)));
            }
        });
    }

    @Test public void parallel() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    "parallel a: {\n" +
                    "  node {withEnv(['TOOL=aloc']) {semaphore 'a'; isUnix() ? sh('echo TOOL=$TOOL') : bat('echo TOOL=%TOOL%')}}\n" +
                    "}, b: {\n" +
                    "  node {withEnv(['TOOL=bloc']) {semaphore 'b'; isUnix() ? sh('echo TOOL=$TOOL') : bat('echo TOOL=%TOOL%')}}\n" +
                    "}", true));
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("a/1", b);
                SemaphoreStep.waitForStart("b/1", b);
                SemaphoreStep.success("a/1", null);
                SemaphoreStep.success("b/1", null);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
                story.j.assertLogContains("[a] TOOL=aloc", b);
                story.j.assertLogContains("[b] TOOL=bloc", b);
            }
        });
    }

    @Test public void restarting() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    "def show(which) {\n" +
                    "  echo \"groovy ${which} ${env.TESTVAR}:\"\n" +
                    "  isUnix() ? sh(\"echo shell ${which} \\$TESTVAR:\") : bat(\"echo shell ${which} %TESTVAR%:\")\n" +
                    "}\n" +
                    "node {\n" +
                    "  withEnv(['TESTVAR=val']) {\n" +
                    "    show 'before'\n" +
                    "    semaphore 'restarting'\n" +
                    "    show 'after'\n" +
                    "  }\n" +
                    "  show 'outside'\n" +
                    "}", true));
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("restarting/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                SemaphoreStep.success("restarting/1", null);
                WorkflowRun b = story.j.assertBuildStatusSuccess(story.j.waitForCompletion(story.j.jenkins.getItemByFullName("p", WorkflowJob.class).getLastBuild()));
                story.j.assertLogContains("groovy before val:", b);
                story.j.assertLogContains("shell before val:", b);
                story.j.assertLogContains("groovy after val:", b);
                story.j.assertLogContains("shell after val:", b);
                story.j.assertLogContains("groovy outside null:", b);
                story.j.assertLogContains("shell outside :", b);
            }
        });
    }

    @Test public void nested() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  withEnv(['A=one']) {\n" +
                    "    withEnv(['B=two']) {\n" +
                    "      isUnix() ? sh('echo A=$A B=$B') : bat('echo A=%A% B=%B%')\n" +
                    "    }\n" +
                    "  }\n" +
                    "}", true));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("A=one B=two", b);
            }
        });
    }

    @Test public void configRoundTrip() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                configRoundTrip(Collections.<String>emptyList());
                configRoundTrip(Collections.singletonList("VAR1=val1"));
                configRoundTrip(Arrays.asList("VAR1=val1", "VAR2=val2"));
            }
            private void configRoundTrip(List<String> overrides) throws Exception {
                assertEquals(overrides, new StepConfigTester(story.j).configRoundTrip(new EnvStep(overrides)).getOverrides());
            }
        });
    }

    // TODO add @LocalData serialForm test proving compatibility with executions dating back to workflow 1.4.3 on 1.580.1

}
