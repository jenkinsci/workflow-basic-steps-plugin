/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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

import static org.jenkinsci.plugins.workflow.steps.CatchErrorStepTest.assertCatchError;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
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

@Issue("JENKINS-45579")
@WithJenkins
class WarnErrorStepTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void smokes() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("""
                        warnError('caught error') {
                          error 'oops'
                        }""", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertCatchError(r, b, Result.UNSTABLE, Result.UNSTABLE, true);
    }

    @Test
    void requiresMessage() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("""
                        warnError() {
                          error 'oops'
                        }""", true));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        r.assertLogContains("A non-empty message is required", b);
    }

    @Test
    void canCatchTimeoutsWithOption() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("""
                        timeout(time: 1, unit: 'SECONDS') {
                          warnError(message: 'caught error', catchInterruptions: true) {
                            sleep 5
                          }
                        }""", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertCatchError(r, b, Result.ABORTED, Result.ABORTED, true);
    }

    @Test
    void avoidsCatchingTimeoutsByDefault() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("""
                        timeout(time: 1, unit: 'SECONDS') {
                          warnError('caught error') {
                            sleep 5
                          }
                        }""", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertCatchError(r, b, Result.ABORTED, null, false);
    }

    @Test
    void canCatchAttemptsToStopBuildWithOption() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("""
                        warnError(message: 'caught error', catchInterruptions: true) {
                          semaphore 'ready'
                        }""", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("ready/1", b);
        b.doStop();
        assertCatchError(r, b, Result.ABORTED, Result.ABORTED, true);
    }

    @Test
    void avoidsCatchingAttemptsToStopBuildByDefault() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("""
                        warnError('caught error') {
                          semaphore 'ready'
                        }""", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("ready/1", b);
        b.doStop();
        assertCatchError(r, b, Result.ABORTED, null, false);
    }

    @Test
    void gracefulSupersession() throws Exception {
        var p = r.jenkins.createProject(WorkflowJob.class, "p");
        var dcb = new DisableConcurrentBuildsJobProperty();
        dcb.setAbortPrevious(true);
        p.addProperty(dcb);
        p.setDefinition(new CpsFlowDefinition("""
            warnError('caught abort') {
              semaphore 'ready'
            }
            echo 'later stage'
            if (BUILD_NUMBER == '1') {
              sleep 999
            }
        """, true));
        var b1 = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("ready/1", b1);
        SemaphoreStep.success("ready/2", null);
        r.buildAndAssertStatus(Result.SUCCESS, p); // b2
        // With catchInterruptions defaulting to true, runs later stage
        // (and with workflow-job-plugin #804, winds up ABORTED rather than exiting gracefully)
        r.assertBuildStatus(Result.NOT_BUILT, r.waitForCompletion(b1));
        r.assertLogNotContains("later stage", b1);
    }
}
