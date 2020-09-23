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

import hudson.model.Result;
import hudson.model.User;
import jenkins.model.CauseOfInterruption;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class CatchErrorStepTest {
    @ClassRule public static BuildWatcher w = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void specialStatus() throws Exception {
        User.getById("smrt", true);
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "catchError {\n" +
                "  semaphore 'specialStatus'\n" +
                "}", true));
        SemaphoreStep.failure("specialStatus/1", new FlowInterruptedException(Result.UNSTABLE, new CauseOfInterruption.UserInterruption("smrt")));
        WorkflowRun b = p.scheduleBuild2(0).get();
        r.assertLogContains("smrt", r.assertBuildStatus(Result.UNSTABLE, b));
        /* TODO fixing this is trickier since CpsFlowExecution.setResult does not implement a public method, and anyway CatchErrorStep in its current location could not refer to FlowExecution:
        List<FlowNode> heads = b.getExecution().getCurrentHeads();
        assertEquals(1, heads.size());
        assertEquals(Result.UNSTABLE, ((FlowEndNode) heads.get(0)).getResult());
        */
    }

    @LocalData
    @Test public void serialFormWhenBuildResultOptionDidNotExist() throws Exception {
        // Local data created using workflow-basic-steps 2.15 with the following Pipeline:
        /*
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "catchError {\n" +
                "  sleep 30\n" +
                "  error 'oops'\n" +
                "}\n" +
                "echo 'execution continued'\n", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        */
        WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
        WorkflowRun b = p.getBuildByNumber(1);
        r.waitForCompletion(b);
        r.assertBuildStatus(Result.FAILURE, b);
        r.assertLogContains("oops", b);
        r.assertLogContains("execution continued", b);
    }

    @LocalData
    @Test public void serialFormWhenTypeOfBuildResultFieldWasResult() throws Exception {
        // Local data created using workflow-basic-steps 2.16 with the following Pipeline:
        /*
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {\n" +
                "  sleep 30\n" +
                "  error 'oops'\n" +
                "}\n" +
                "echo 'execution continued'\n", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        */
        WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
        WorkflowRun b = p.getBuildByNumber(1);
        r.waitForCompletion(b);
        r.assertBuildStatus(Result.UNSTABLE, b);
        r.assertLogContains("oops", b);
        r.assertLogContains("execution continued", b);
    }

    @Test public void customBuildResult() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "catchError(message: 'caught error', buildResult: 'unstable') {\n" +
                "  error 'oops'\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertCatchError(r, b, Result.UNSTABLE, null, true);
    }

    @Test public void invalidBuildResult() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "catchError(buildResult: 'typo') {\n" +
                "  error 'oops'\n" +
                "}", true));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        r.assertLogContains("buildResult is invalid: typo", b);
    }

    @Issue("JENKINS-45579")
    @Test public void customStageResult() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "catchError(message: 'caught error', stageResult: 'failure') {\n" +
                "  error 'oops'\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertCatchError(r, b, Result.FAILURE, Result.FAILURE, true);
    }

    @Test public void invalidStageResult() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "catchError(stageResult: 'typo') {\n" +
                "  error 'oops'\n" +
                "}", true));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        r.assertLogContains("stageResult is invalid: typo", b);
    }

    @Issue("JENKINS-45579")
    @Test public void stepResultOnly() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "catchError(message: 'caught error', buildResult: 'success', stageResult: 'unstable') {\n" +
                "  error 'oops'\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertCatchError(r, b, Result.SUCCESS, Result.UNSTABLE, true);
    }

    @Test public void catchesTimeoutsByDefault() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "timeout(time: 250, unit: 'MILLISECONDS') {\n" +
                "  catchError(message: 'caught error') {\n" +
                "    sleep 1\n" +
                "  }\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertCatchError(r, b, Result.ABORTED, Result.ABORTED, true);
    }

    @Test public void canAvoidCatchingTimeoutsWithOption() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "timeout(time: 250, unit: 'MILLISECONDS') {\n" +
                "  catchError(message: 'caught error', catchInterruptions: false) {\n" +
                "    sleep 1\n" +
                "  }\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertCatchError(r, b, Result.ABORTED, null, false);
    }

    @Test public void catchesAttemptsToStopBuildByDefault() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "catchError(message: 'caught error') {\n" +
                "  semaphore 'ready'\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("ready/1", b);
        b.doStop();
        assertCatchError(r, b, Result.ABORTED, Result.ABORTED, true);
    }

    @Test public void canAvoidCatchingAttemptsToStopBuildWithOption() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "catchError(message: 'caught error', catchInterruptions: false) {\n" +
                "  semaphore 'ready'\n" +
                "}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("ready/1", b);
        b.doStop();
        assertCatchError(r, b, Result.ABORTED, null, false);
    }

    @Issue("JENKINS-60354")
    @Test public void catchesDownstreamBuildFailureEvenWhenNotCatchingInterruptions() throws Exception {
        WorkflowJob ds = r.createProject(WorkflowJob.class);
        ds.setDefinition(new CpsFlowDefinition("error 'oops!'", true));
        WorkflowJob us = r.createProject(WorkflowJob.class);
        us.setDefinition(new CpsFlowDefinition(
                "int count = 1\n" +
                "catchError(message: 'caught error', catchInterruptions: false) {\n" +
                "  build(job: '"+ds.getName()+"')\n" +
                "}\n", true));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, us.scheduleBuild2(0));
        assertCatchError(r, b, Result.FAILURE, Result.FAILURE, true);
    }

    public static void assertCatchError(JenkinsRule r, WorkflowRun run, Result buildResult, Result warningActionResult, boolean expectingCatch) throws Exception {
        r.waitForCompletion(run);
        r.assertBuildStatus(buildResult, run);
        if (expectingCatch) {
            r.assertLogContains("caught error", run);
        } else {
            r.assertLogNotContains("caught error", run);
        }
        FlowNode warningNode = new DepthFirstScanner().findFirstMatch(run.getExecution(),
                node -> node.getPersistentAction(WarningAction.class) != null);
        if (warningActionResult != null) {
            assertThat(warningNode, notNullValue());
            WarningAction warning = warningNode.getPersistentAction(WarningAction.class);
            assertThat(warning, notNullValue());
            assertThat(warning.getResult(), equalTo(warningActionResult));
            assertThat(warning.getMessage(), equalTo("caught error"));
        } else {
            assertThat(warningNode, nullValue());
        }
    }

}
