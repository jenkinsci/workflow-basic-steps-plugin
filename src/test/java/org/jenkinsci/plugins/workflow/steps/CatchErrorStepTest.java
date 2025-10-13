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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import hudson.model.Result;
import hudson.model.User;
import jenkins.model.CauseOfInterruption;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.job.properties.DisableConcurrentBuildsJobProperty;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class CatchErrorStepTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void specialStatus() throws Exception {
        User.getById("smrt", true);
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                catchError {
                        semaphore 'specialStatus'
                }
                """,
                true));
        SemaphoreStep.failure(
                "specialStatus/1",
                new FlowInterruptedException(Result.UNSTABLE, new CauseOfInterruption.UserInterruption("smrt")));
        WorkflowRun b = p.scheduleBuild2(0).get();
        r.assertLogContains("smrt", r.assertBuildStatus(Result.UNSTABLE, b));
        /* TODO fixing this is trickier since CpsFlowExecution.setResult does not implement a public method, and anyway CatchErrorStep in its current location could not refer to FlowExecution:
        List<FlowNode> heads = b.getExecution().getCurrentHeads();
        assertEquals(1, heads.size());
        assertEquals(Result.UNSTABLE, ((FlowEndNode) heads.get(0)).getResult());
        */
    }

    @LocalData
    @Test
    void serialFormWhenBuildResultOptionDidNotExist() throws Exception {
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
    @Test
    void serialFormWhenTypeOfBuildResultFieldWasResult() throws Exception {
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

    @Test
    void customBuildResult() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                catchError(message: 'caught error', buildResult: 'unstable') {
                        error 'oops'
                }
                """,
                true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertCatchError(r, b, Result.UNSTABLE, null, true);
        r.assertLogContains("Setting overall build result to UNSTABLE", b);
    }

    @Test
    void invalidBuildResult() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                catchError(buildResult: 'typo') {
                        error 'oops'
                }
                """,
                true));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        r.assertLogContains("buildResult is invalid: typo", b);
    }

    @Issue("JENKINS-45579")
    @Test
    void customStageResult() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                catchError(message: 'caught error', stageResult: 'failure') {
                        error 'oops'
                }
                """,
                true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertCatchError(r, b, Result.FAILURE, Result.FAILURE, true);
    }

    @Test
    void invalidStageResult() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                catchError(stageResult: 'typo') {
                        error 'oops'
                }
                """,
                true));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        r.assertLogContains("stageResult is invalid: typo", b);
    }

    @Issue("JENKINS-45579")
    @Test
    void stepResultOnly() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                catchError(message: 'caught error', buildResult: 'success', stageResult: 'unstable') {
                  error 'oops'
                }
                """,
                true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertCatchError(r, b, Result.SUCCESS, Result.UNSTABLE, true);
    }

    @Test
    void catchesInterruptionsByDefault() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                import jenkins.model.CauseOfInterruption
                import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
                catchError(message: 'caught error') {
                        throw new FlowInterruptedException(Result.ABORTED, true, new CauseOfInterruption[0])
                }
                """,
                false));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertCatchError(r, b, Result.ABORTED, Result.ABORTED, true);
    }

    @Test
    void canAvoidCatchingInterruptionsWithOption() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                import jenkins.model.CauseOfInterruption
                import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
                catchError(message: 'caught error', catchInterruptions: false) {
                        throw new FlowInterruptedException(Result.ABORTED, true, new CauseOfInterruption[0])
                }
                """,
                false));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertCatchError(r, b, Result.ABORTED, null, false);
    }

    @Test
    void catchesAttemptsToStopBuildByDefault() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                catchError(message: 'caught error') {
                        semaphore 'ready'
                }
                """,
                true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("ready/1", b);
        b.doStop();
        assertCatchError(r, b, Result.ABORTED, Result.ABORTED, true);
    }

    @Test
    void canAvoidCatchingAttemptsToStopBuildWithOption() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                catchError(message: 'caught error', catchInterruptions: false) {
                        semaphore 'ready'
                }
                """,
                true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("ready/1", b);
        b.doStop();
        assertCatchError(r, b, Result.ABORTED, null, false);
    }

    @Issue("JENKINS-60354")
    @Test
    void catchesDownstreamBuildFailureEvenWhenNotCatchingInterruptions() throws Exception {
        WorkflowJob ds = r.createProject(WorkflowJob.class, "ds");
        ds.setDefinition(new CpsFlowDefinition("error 'oops!'", true));
        WorkflowJob us = r.createProject(WorkflowJob.class, "us");
        us.setDefinition(new CpsFlowDefinition(
                """
                int count = 1
                catchError(message: 'caught error', catchInterruptions: false) {
                  build 'ds'
                }
                """,
                true));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, us.scheduleBuild2(0));
        assertCatchError(r, b, Result.FAILURE, Result.FAILURE, true);
    }

    @Test
    void abortPreviousWithCatchError() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(
                "catchError(catchInterruptions: false) {semaphore 'main'}; semaphore 'post'", true));
        p.setConcurrentBuild(false);
        p.getProperty(DisableConcurrentBuildsJobProperty.class).setAbortPrevious(true);
        WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("main/1", b1);
        WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("main/2", b2);
        r.assertBuildStatus(Result.NOT_BUILT, r.waitForCompletion(b1));
        SemaphoreStep.success("main/2", null);
        SemaphoreStep.success("post/1", null);
        r.assertBuildStatusSuccess(r.waitForCompletion(b2));
    }

    public static void assertCatchError(
            JenkinsRule r, WorkflowRun run, Result buildResult, Result warningActionResult, boolean expectingCatch)
            throws Exception {
        r.waitForCompletion(run);
        r.assertBuildStatus(buildResult, run);
        if (expectingCatch) {
            r.assertLogContains("caught error", run);
        } else {
            r.assertLogNotContains("caught error", run);
        }
        FlowNode warningNode = new DepthFirstScanner()
                .findFirstMatch(run.getExecution(), node -> node.getPersistentAction(WarningAction.class) != null);
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
