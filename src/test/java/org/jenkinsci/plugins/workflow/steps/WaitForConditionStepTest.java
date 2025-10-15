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

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.AbortException;
import hudson.Util;
import hudson.model.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

class WaitForConditionStepTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();

    @RegisterExtension
    private final JenkinsSessionExtension sessions = new JenkinsSessionExtension();

    @Test
    void simple() throws Throwable {
        sessions.then(j -> {
            WorkflowJob p = j.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("waitUntil {semaphore 'wait'}; semaphore 'waited'", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b);
            SemaphoreStep.success("wait/1", false);
            SemaphoreStep.waitForStart("wait/2", b);
            SemaphoreStep.success("wait/2", false);
            SemaphoreStep.waitForStart("wait/3", b);
            SemaphoreStep.success("wait/3", true);
            SemaphoreStep.waitForStart("waited/1", b);
            SemaphoreStep.success("waited/1", null);
            j.assertLogContains(
                    "Will try again after " + Util.getTimeSpanString(WaitForConditionStep.MIN_RECURRENCE_PERIOD),
                    j.assertBuildStatusSuccess(j.waitForCompletion(b)));
        });
    }

    @Test
    void initialRecurrence() throws Throwable {
        sessions.then(j -> {
            WorkflowJob p = j.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                    "waitUntil(initialRecurrencePeriod: 999) {semaphore 'wait'}; semaphore 'waited'", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b);
            SemaphoreStep.success("wait/1", false);
            SemaphoreStep.waitForStart("wait/2", b);
            SemaphoreStep.success("wait/2", false);
            SemaphoreStep.waitForStart("wait/3", b);
            SemaphoreStep.success("wait/3", true);
            SemaphoreStep.waitForStart("waited/1", b);
            SemaphoreStep.success("waited/1", null);
            j.assertLogContains(
                    "Will try again after " + Util.getTimeSpanString(999),
                    j.assertBuildStatusSuccess(j.waitForCompletion(b)));
        });
    }

    @Test
    void failure() throws Throwable {
        sessions.then(j -> {
            WorkflowJob p = j.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("waitUntil {semaphore 'wait'}", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b);
            SemaphoreStep.success("wait/1", false);
            SemaphoreStep.waitForStart("wait/2", b);
            String message = "broken condition";
            SemaphoreStep.failure("wait/2", new AbortException(message));
            // TODO the following fails (missing message) when run as part of whole suite, but not standalone:
            // j.assertLogContains(message, j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b)));
            j.waitForCompletion(b);
            j.assertBuildStatus(Result.FAILURE, b);
            // TODO observed to flake on windows-8-2.32.3:
            // see two `semaphore`s and a "Will try again after 0.25 sec" but no such message
            j.waitForMessage(message, b);
        });
    }

    @Test
    void catchErrors() throws Throwable {
        sessions.then(j -> {
            WorkflowJob p = j.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                    """
                    node {
                      waitUntil {
                        try {
                          readFile 'flag'
                          true
                        // Note that catching a specific type verifies JENKINS-26164:
                        } catch (FileNotFoundException | java.nio.file.NoSuchFileException x) {
                          // x.printStackTrace()
                          semaphore 'wait'
                          false
                        }
                      }
                    }
                    echo 'finished waiting'
                    """,
                    true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b);
            j.jenkins.getWorkspaceFor(p).child("flag").write("", null);
            SemaphoreStep.success("wait/1", null);
            j.assertLogContains("finished waiting", j.assertBuildStatusSuccess(j.waitForCompletion(b)));
        });
    }

    @Test
    void restartDuringBody() throws Throwable {
        sessions.then(j -> {
            WorkflowJob p = j.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("waitUntil {semaphore 'wait'}; echo 'finished waiting'", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b);
            SemaphoreStep.success("wait/1", false);
            SemaphoreStep.waitForStart("wait/2", b);
        });
        sessions.then(j -> {
            WorkflowRun b = j.jenkins.getItemByFullName("p", WorkflowJob.class).getBuildByNumber(1);
            SemaphoreStep.success("wait/2", false);
            SemaphoreStep.waitForStart("wait/3", b);
            SemaphoreStep.success("wait/3", true);
            j.assertLogContains("finished waiting", j.assertBuildStatusSuccess(j.waitForCompletion(b)));
        });
    }

    @Test
    void restartDuringDelay() throws Throwable {
        sessions.then(j -> {
            WorkflowJob p = j.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("waitUntil {semaphore 'wait'}; echo 'finished waiting'", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b);
            final List<WaitForConditionStep.Execution> executions = new ArrayList<>();
            StepExecution.acceptAll(WaitForConditionStep.Execution.class, executions::add)
                    .get();
            assertEquals(1, executions.size());
            SemaphoreStep.success("wait/1", false);
            SemaphoreStep.waitForStart("wait/2", b);
            final long LONG_TIME = Long.MAX_VALUE / /* > RECURRENCE_PERIOD_BACKOFF */ 10;
            executions.get(0).recurrencePeriod = LONG_TIME;
            SemaphoreStep.success("wait/2", false);
            await().timeout(5, TimeUnit.SECONDS).until(() -> executions.get(0).recurrencePeriod != LONG_TIME);
            j.waitForMessage("Will try again after " + Util.getTimeSpanString(LONG_TIME), b);
            // timer is now waiting for a long time
        });
        sessions.then(j -> {
            WorkflowRun b = j.jenkins.getItemByFullName("p", WorkflowJob.class).getBuildByNumber(1);
            // TODO pending https://github.com/jenkinsci/workflow-cps-plugin/pull/141 this will flake out
            SemaphoreStep.waitForStart("wait/3", b);
            SemaphoreStep.success("wait/3", false);
            SemaphoreStep.waitForStart("wait/4", b);
            SemaphoreStep.success("wait/4", true);
            j.assertLogContains("finished waiting", j.assertBuildStatusSuccess(j.waitForCompletion(b)));
        });
    }

    @Test
    void quiet() throws Throwable {
        sessions.then(j -> {
            WorkflowJob p = j.createProject(WorkflowJob.class, "p");
            p.setDefinition(
                    new CpsFlowDefinition("waitUntil(quiet: true) {semaphore 'wait'}; semaphore 'waited'", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b);
            SemaphoreStep.success("wait/1", true);
            SemaphoreStep.waitForStart("waited/1", b);
            SemaphoreStep.success("waited/1", null);
            j.assertLogNotContains(
                    "Will try again after " + Util.getTimeSpanString(WaitForConditionStep.MIN_RECURRENCE_PERIOD),
                    j.assertBuildStatusSuccess(j.waitForCompletion(b)));
        });
    }

    // TODO add @LocalData serialForm test proving compatibility with executions
    // dating back to workflow 1.4.3 on 1.580.1
    // (same for RetryStep)

}
