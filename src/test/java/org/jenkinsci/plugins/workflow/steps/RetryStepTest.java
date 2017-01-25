package org.jenkinsci.plugins.workflow.steps;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Tests {@link RetryStep}.
 */
public class RetryStepTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test(timeout = 50000)
    @Issue("JENKINS-41276")
    public void abortShouldNotRetry() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "int count = 0; retry(3) { echo 'trying '+(count++); semaphore 'start'; sleep 10; echo 'NotHere' }", false));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("start/1", b);
        b.doStop();
        b = r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b));
        r.assertLogContains("trying 0", b);
        r.assertLogContains("Aborted by anonymous", b);
        r.assertLogNotContains("trying 1", b);
        r.assertLogNotContains("trying 2", b);
        r.assertLogNotContains("NotHere", b);

    }

}