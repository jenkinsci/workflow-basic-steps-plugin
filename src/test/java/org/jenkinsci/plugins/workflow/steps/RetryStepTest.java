package org.jenkinsci.plugins.workflow.steps;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.Result;
import hudson.model.User;
import hudson.model.queue.QueueTaskFuture;
import hudson.security.ACL;
import hudson.security.ACLContext;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStepExecution;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import java.util.concurrent.TimeUnit;

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
        try (ACLContext context = ACL.as(User.getById("dev", true))) {
            b.getExecutor().doStop();
        }
        r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b));
        r.assertLogContains("trying 0", b);
        r.assertLogContains("Aborted by dev", b);
        r.assertLogNotContains("trying 1", b);
        r.assertLogNotContains("trying 2", b);
        r.assertLogNotContains("NotHere", b);

    }

    @Issue("JENKINS-44379")
    @Test
    public void inputAbortShouldNotRetry() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("int count = 0\n" +
                "retry(3) {\n" +
                "  echo 'trying '+(count++)\n" +
                "  input id: 'InputX', message: 'OK?', ok: 'Yes'\n" +
                "}\n", true));

        QueueTaskFuture<WorkflowRun> queueTaskFuture = p.scheduleBuild2(0);
        WorkflowRun run = queueTaskFuture.getStartCondition().get();
        CpsFlowExecution execution = (CpsFlowExecution) run.getExecutionPromise().get();

        while (run.getAction(InputAction.class) == null) {
            execution.waitForSuspension();
        }

        InputAction inputAction = run.getAction(InputAction.class);
        InputStepExecution is = inputAction.getExecution("InputX");
        HtmlPage page = r.createWebClient().getPage(run, inputAction.getUrlName());

        r.submit(page.getFormByName(is.getId()), "abort");
        assertEquals(0, inputAction.getExecutions().size());
        queueTaskFuture.get();

        r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(run));

        r.assertLogContains("trying 0", run);
        r.assertLogNotContains("trying 1", run);
        r.assertLogNotContains("trying 2", run);
    }

    @Test
    public void stackTraceOnError() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(
                new CpsFlowDefinition(
                        "def count = 0\n"
                                + "retry(2) {\n"
                                + "  count += 1\n"
                                + "  echo 'Try #' + count\n"
                                + "  if (count == 1) {\n"
                                + "    throw new Exception('foo')\n"
                                + "  }\n"
                                + "  echo 'Done!'\n"
                                + "}\n",
                        true));

        WorkflowRun run = r.buildAndAssertSuccess(p);
        r.assertLogContains("Try #1", run);
        r.assertLogContains("ERROR: Execution failed", run);
        r.assertLogContains("java.lang.Exception: foo", run);
        r.assertLogContains("\tat ", run);
        r.assertLogContains("Try #2", run);
        r.assertLogContains("Done!", run);
    }

    @Issue("JENKINS-60354")
    @Test
    public void downstreamBuildFailureShouldRetry() throws Exception {
        WorkflowJob ds = r.createProject(WorkflowJob.class);
        ds.setDefinition(new CpsFlowDefinition("error 'oops!'", true));
        WorkflowJob us = r.createProject(WorkflowJob.class);
        us.setDefinition(new CpsFlowDefinition(
                "int count = 1\n" +
                "retry(3) {\n" +
                "  echo(/trying ${count++}/)\n" +
                "  build(job: '"+ds.getName()+"')\n" +
                "}\n", true));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, us.scheduleBuild2(0));
        r.assertLogContains("trying 1", b);
        r.assertLogContains("trying 2", b);
        r.assertLogContains("trying 3", b);
    }

    @Issue("JENKINS-44379")
    @Test
    public void shouldNotRetryAfterOuterTimeout() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(
                new CpsFlowDefinition(
                        "int count = 0\n"
                                + "timeout(time: 5, unit: 'SECONDS') {\n"
                                + "  retry(3) {\n"
                                + "    echo 'try ' + count++\n"
                                + "    sleep 15\n"
                                + "    error 'failure'\n"
                                + "  }\n"
                                + "}\n",
                        true));

        WorkflowRun run = r.buildAndAssertStatus(Result.ABORTED, p);
        r.assertLogContains("Timeout has been exceeded", run);
        r.assertLogContains("try 0", run);
        r.assertLogNotContains("try 1", run);
        r.assertLogNotContains("try 2", run);
    }

    @Issue("JENKINS-51454")
    @Test
    public void shouldRetryAfterInnerTimeout() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(
                new CpsFlowDefinition(
                        "int count = 0\n"
                                + "retry(3) {\n"
                                + "  timeout(time: 5, unit: 'SECONDS') {\n"
                                + "    echo 'try ' + count++\n"
                                + "    sleep 15\n"
                                + "    error 'failure'\n"
                                + "  }\n"
                                + "}\n",
                        true));

        WorkflowRun run = r.buildAndAssertStatus(Result.ABORTED, p);
        r.assertLogContains("Timeout has been exceeded", run);
        r.assertLogContains("try 0", run);
        r.assertLogContains("try 1", run);
        r.assertLogContains("try 2", run);
    }

    @Test
    public void retryTimeout() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
            "int i = 0;\n" +
            "retry(count: 3, timeDelay: 10, unit: 'SECONDS', useRetryDelay: true) {\n" +
            "    println 'Trying!'\n" +
            "    if (i++ < 2) error('oops');\n" +
            "    println 'Done!'\n" +
            "}\n" +
            "println 'Over!'"
        , true));
    
        long before = System.currentTimeMillis();
        QueueTaskFuture<WorkflowRun> f = p.scheduleBuild2(0);
        long after = System.currentTimeMillis();
        long difference = after - before;
        long timeInSeconds = TimeUnit.MILLISECONDS.convert(difference, TimeUnit.SECONDS);
        assertTrue(timeInSeconds > 20);
        WorkflowRun b = r.assertBuildStatusSuccess(f);
    
        String log = JenkinsRule.getLog(b);
        r.assertLogNotContains("\tat ", b);
    
        int idx = 0;
        for (String msg : new String[] {
            "Trying!",
            "oops",
            "Will try again",
            "Retrying",
            "Trying!",
            "oops",
            "Will try again",
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

    @Test
    public void stackTraceOnErrorWithTimeout() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(
                new CpsFlowDefinition(
                        "def count = 0\n"
                                + "retry(count: 2, timeDelay: 10, unit: 'SECONDS', useRetryDelay: true) {\n"
                                + "  count += 1\n"
                                + "  echo 'Try #' + count\n"
                                + "  if (count == 1) {\n"
                                + "    throw new Exception('foo')\n"
                                + "  }\n"
                                + "  echo 'Done!'\n"
                                + "}\n",
                        true));

        WorkflowRun run = r.buildAndAssertSuccess(p);
        r.assertLogContains("Try #1", run);
        r.assertLogContains("ERROR: Execution failed", run);
        r.assertLogContains("java.lang.Exception: foo", run);
        r.assertLogContains("\tat ", run);
        r.assertLogContains("Try #2", run);
        r.assertLogContains("Done!", run);
    }
}
