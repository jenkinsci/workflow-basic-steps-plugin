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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

public class EnvStepTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();

    @RegisterExtension
    private final JenkinsSessionExtension sessions = new JenkinsSessionExtension();

    @Test
    void overriding() throws Throwable {
        sessions.then(j -> {
            WorkflowJob p = j.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                    """
                                env.CUSTOM = 'initial'
                                env.FOOPATH = node {isUnix() ? '/opt/foos' : 'C:\\\\foos'}
                                env.NULLED = 'outside'
                                node {
                                  withEnv(['CUSTOM=override', 'NOVEL=val', 'BUILD_TAG=custom', 'NULLED=', isUnix() ? 'FOOPATH+BALL=/opt/ball' : 'FOOPATH+BALL=C:\\\\ball']) {
                                    isUnix() ? sh('echo inside CUSTOM=$CUSTOM NOVEL=$NOVEL BUILD_TAG=$BUILD_TAG NULLED=$NULLED FOOPATH=$FOOPATH:') : bat('echo inside CUSTOM=%CUSTOM% NOVEL=%NOVEL% BUILD_TAG=%BUILD_TAG% NULLED=%NULLED% FOOPATH=%FOOPATH%;')
                                    echo "groovy NULLED=${env.NULLED}"
                                  }
                                  isUnix() ? sh('echo outside CUSTOM=$CUSTOM NOVEL=$NOVEL NULLED=outside') : bat('echo outside CUSTOM=%CUSTOM% NOVEL=%NOVEL% NULLED=outside')
                                }""",
                    true));
            WorkflowRun b = j.buildAndAssertSuccess(p);
            j.assertLogContains(
                    Functions.isWindows()
                            ? "inside CUSTOM=override NOVEL=val BUILD_TAG=custom NULLED= FOOPATH=C:\\ball;C:\\foos;"
                            : "inside CUSTOM=override NOVEL=val BUILD_TAG=custom NULLED= FOOPATH=/opt/ball:/opt/foos:",
                    b);
            j.assertLogContains("groovy NULLED=null", b);
            j.assertLogContains("outside CUSTOM=initial NOVEL= NULLED=outside", b);
            List<FlowNode> coreStepNodes = new DepthFirstScanner()
                    .filteredNodes(
                            b.getExecution(),
                            Predicates.and(
                                    new NodeStepTypePredicate("withEnv"),
                                    n -> n instanceof StepStartNode && !((StepStartNode) n).isBody()));
            assertThat(coreStepNodes, Matchers.hasSize(1));
            assertEquals(
                    "CUSTOM, NOVEL, BUILD_TAG, NULLED, FOOPATH+BALL",
                    ArgumentsAction.getStepArgumentsAsString(coreStepNodes.get(0)));
        });
    }

    @Test
    void parallel() throws Throwable {
        sessions.then(j -> {
            WorkflowJob p = j.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                    """
                                parallel a: {
                                  node {withEnv(['TOOL=aloc']) {semaphore 'a'; isUnix() ? sh('echo a TOOL=$TOOL') : bat('echo a TOOL=%TOOL%')}}
                                }, b: {
                                  node {withEnv(['TOOL=bloc']) {semaphore 'b'; isUnix() ? sh('echo b TOOL=$TOOL') : bat('echo b TOOL=%TOOL%')}}
                                }""",
                    true));
            WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
            SemaphoreStep.waitForStart("a/1", b);
            SemaphoreStep.waitForStart("b/1", b);
            SemaphoreStep.success("a/1", null);
            SemaphoreStep.success("b/1", null);
            j.assertBuildStatusSuccess(j.waitForCompletion(b));
            j.assertLogContains("a TOOL=aloc", b);
            j.assertLogContains("b TOOL=bloc", b);
        });
    }

    @Test
    void restarting() throws Throwable {
        sessions.then(j -> {
            WorkflowJob p = j.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                    """
                                def show(which) {
                                  echo "groovy ${which} ${env.TESTVAR}:"
                                  isUnix() ? sh("echo shell ${which} \\$TESTVAR:") : bat("echo shell ${which} %TESTVAR%:")
                                }
                                node {
                                  withEnv(['TESTVAR=val']) {
                                    show 'before'
                                    semaphore 'restarting'
                                    show 'after'
                                  }
                                  show 'outside'
                                }""",
                    true));
            WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
            SemaphoreStep.waitForStart("restarting/1", b);
        });
        sessions.then(j -> {
            SemaphoreStep.success("restarting/1", null);
            WorkflowRun b = j.assertBuildStatusSuccess(j.waitForCompletion(
                    j.jenkins.getItemByFullName("p", WorkflowJob.class).getLastBuild()));
            j.assertLogContains("groovy before val:", b);
            j.assertLogContains("shell before val:", b);
            j.assertLogContains("groovy after val:", b);
            j.assertLogContains("shell after val:", b);
            j.assertLogContains("groovy outside null:", b);
            j.assertLogContains("shell outside :", b);
        });
    }

    @Test
    void nested() throws Throwable {
        sessions.then(j -> {
            WorkflowJob p = j.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                    """
                                node {
                                  withEnv(['A=one']) {
                                    withEnv(['B=two']) {
                                      isUnix() ? sh('echo A=$A B=$B') : bat('echo A=%A% B=%B%')
                                    }
                                  }
                                }""",
                    true));
            WorkflowRun b = j.buildAndAssertSuccess(p);
            j.assertLogContains("A=one B=two", b);
        });
    }

    @Test
    void configRoundTrip() throws Throwable {
        sessions.then(j -> {
            configRoundTrip(Collections.emptyList(), j);
            configRoundTrip(Collections.singletonList("VAR1=val1"), j);
            configRoundTrip(Arrays.asList("VAR1=val1", "VAR2=val2"), j);
        });
    }

    private static void configRoundTrip(List<String> overrides, JenkinsRule j) throws Exception {
        assertEquals(
                overrides,
                new StepConfigTester(j).configRoundTrip(new EnvStep(overrides)).getOverrides());
    }

    // TODO add @LocalData serialForm test proving compatibility with executions
    // dating back to workflow 1.4.3 on 1.580.1

}
