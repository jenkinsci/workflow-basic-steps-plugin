package org.jenkinsci.plugins.workflow.steps;

import hudson.model.Result;
import hudson.model.User;
import hudson.model.queue.QueueTaskFuture;
import hudson.security.ACL;
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

import static org.junit.Assert.*;

/**
 * Tests {@link RetryStep}.
 */
public class RetryStepTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void smokes() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
            "int i = 0;\n" +
            "retry(3) {\n" +
            "    println 'Trying!'\n" +
            "    if (i++ < 2) error('oops');\n" +
            "    println 'Done!'\n" +
            "}\n" +
            "println 'Over!'"
        , true));

        QueueTaskFuture<WorkflowRun> f = p.scheduleBuild2(0);
        WorkflowRun b = r.assertBuildStatusSuccess(f);

        String log = JenkinsRule.getLog(b);
        r.assertLogNotContains("\tat ", b);

        int idx = 0;
        for (String msg : new String[] {
            "Trying!",
            "oops",
            "Retrying",
            "Trying!",
            "oops",
            "Retrying",
            "Trying!",
            "Done!",
            "Over!",
        }) {
            idx = log.indexOf(msg, idx + 1);
            assertTrue(msg + " not found", idx != -1);
        }

        idx = 0;
        for (String msg : new String[] {
            "[Pipeline] retry",
            "[Pipeline] {",
            "[Pipeline] }",
            "[Pipeline] {",
            "[Pipeline] }",
            "[Pipeline] {",
            "[Pipeline] }",
            "[Pipeline] // retry",
        }) {
            idx = log.indexOf(msg, idx + 1);
            assertTrue(msg + " not found", idx != -1);
        }
    }

    @Issue("JENKINS-41276")
    @Test
    public void abortShouldNotRetry() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "int count = 0; retry(3) { echo 'trying '+(count++); semaphore 'start'; echo 'NotHere' } echo 'NotHere'", true));
        final WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("start/1", b);
        ACL.impersonate(User.get("dev").impersonate(), new Runnable() {
            @Override public void run() {
                b.getExecutor().doStop();
            }
        });
        r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b));
        r.assertLogContains("trying 0", b);
        r.assertLogContains("Aborted by dev", b);
        r.assertLogNotContains("trying 1", b);
        r.assertLogNotContains("trying 2", b);
        r.assertLogNotContains("NotHere", b);

    }

}