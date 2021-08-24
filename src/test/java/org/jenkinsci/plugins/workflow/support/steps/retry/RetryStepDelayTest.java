package org.jenkinsci.plugins.workflow.support.steps.retry;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.RetryStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.queue.QueueTaskFuture;

/**
 * Tests {@link RetryStep}.
 */
public class RetryStepDelayTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void retryFixedDelay() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
            "int i = 0;\n" +
            "retry(count: 3, delay: fixed(time: 10, unit: 'SECONDS')) {\n" +
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
        long minimumTimeMilliseconds = TimeUnit.SECONDS.convert(20, TimeUnit.MILLISECONDS);
        assertTrue(difference > minimumTimeMilliseconds);
        WorkflowRun b = r.assertBuildStatusSuccess(f);
    
        String log = JenkinsRule.getLog(b);
        r.assertLogNotContains("\tat ", b);
    
        int idx = 0;
        for (String msg : new String[] {
            "Trying!",
            "oops",
            "Will try again after 10 sec",
            "Retrying",
            "Trying!",
            "oops",
            "Will try again after 10 sec",
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
            "[Pipeline] Start of Pipeline",
            "[Pipeline] retry",
            "[Pipeline] {",
            "[Pipeline] echo",
            "[Pipeline] error",
            "[Pipeline] }",
            "[Pipeline] {",
            "[Pipeline] echo",
            "[Pipeline] error",
            "[Pipeline] }",
            "[Pipeline] {",
            "[Pipeline] echo",
            "[Pipeline] echo",
            "[Pipeline] }",
            "[Pipeline] // retry",
        }) {
            idx = log.indexOf(msg, idx + 1);
            assertTrue(msg + " not found", idx != -1);
        }
    }

    @Test
    public void stackTraceOnErrorWithRetry() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition( new CpsFlowDefinition(
            "int i = 0\n"
                + "retry(count: 2, delay: fixed(time: 5, unit: 'SECONDS')) {\n"
                + "  i += 1\n"
                + "  echo 'Try #' + i\n"
                + "  if (i == 1) {\n"
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

    @Test
    public void retryRandomDelay() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition( new CpsFlowDefinition(
            "int i = 0\n"
                + "retry(count: 4, delay: random(max: 15, min: 5, unit: 'SECONDS')) {\n"
                + "  if (i++ < 3) {\n"
                + "   echo 'Try #' + i\n"
                + "   error('oops');\n"
                + "  }\n"
                + "  println 'Done!'\n"
                + "}\n",
            true));

        WorkflowRun run = r.buildAndAssertSuccess(p);
        r.assertLogContains("Try #1", run);
        r.assertLogContains("ERROR: oops", run);
        r.assertLogContains("Will try again after", run);
        r.assertLogContains("Retrying", run);
        r.assertLogContains("Try #2", run);
        r.assertLogContains("Try #3", run);
        r.assertLogContains("Done!", run);
    }

    @Test
    public void retryExponentialDelay() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition( new CpsFlowDefinition(
            "int i = 0\n"
                + "retry(count: 4, delay: exponential(max: 20, min: 1, multiplier: 2, unit: 'SECONDS')) {\n"
                + "  if (i++ < 3) {\n"
                + "   echo 'Try #' + i\n"
                + "   error('oops');\n"
                + "  }\n"
                + "  println 'Done!'\n"
                + "}\n",
            true));

        WorkflowRun run = r.buildAndAssertSuccess(p);
        r.assertLogContains("Try #1", run);
        r.assertLogContains("ERROR: oops", run);
        r.assertLogContains("Will try again after 5 sec", run);
        r.assertLogContains("Retrying", run);
        r.assertLogContains("Try #2", run);
        r.assertLogContains("Will try again after 9 sec", run);
        r.assertLogContains("Try #3", run);
        r.assertLogContains("Will try again after 17 sec", run);
        r.assertLogContains("Done!", run);
    }

    @Test
    public void retryIncrementalDelay() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition( new CpsFlowDefinition(
            "int i = 0\n"
                + "retry(count: 4, delay: incremental(increment: 2, max: 10, min: 1, unit: 'SECONDS')) {\n"
                + "  if (i++ < 3) {\n"
                + "   echo 'Try #' + i\n"
                + "   error('oops');\n"
                + "  }\n"
                + "  println 'Done!'\n"
                + "}\n",
            true));

        WorkflowRun run = r.buildAndAssertSuccess(p);
        r.assertLogContains("Try #1", run);
        r.assertLogContains("ERROR: oops", run);
        r.assertLogContains("Will try again after 1 sec", run);
        r.assertLogContains("Retrying", run);
        r.assertLogContains("Try #2", run);
        r.assertLogContains("Will try again after 3 sec", run);
        r.assertLogContains("Try #3", run);
        r.assertLogContains("Will try again after 5 sec", run);
        r.assertLogContains("Done!", run);
    }

    @Test
    public void retryRandomExponentialDelay() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition( new CpsFlowDefinition(
            "int i = 0\n"
                + "retry(count: 6, delay: randomExponential(max: 10, multiplier: 2, unit: 'SECONDS')) {\n"
                + "  if (i++ < 5) {\n"
                + "   echo 'Try #' + i\n"
                + "   error('oops');\n"
                + "  }\n"
                + "  println 'Done!'\n"
                + "}\n",
            true));

        WorkflowRun run = r.buildAndAssertSuccess(p);

        r.assertLogContains("Try #1", run);
        r.assertLogContains("ERROR: oops", run);
        r.assertLogContains("Will try again after 4 sec", run);
        r.assertLogContains("Retrying", run);
        r.assertLogContains("Try #2", run);
        r.assertLogContains("Will try again after 8 sec", run);
        r.assertLogContains("Try #3", run);
        r.assertLogContains("Will try again after 10 sec", run);
        r.assertLogContains("Try #4", run);
        r.assertLogContains("Try #5", run);
        r.assertLogContains("Done!", run);
    }
}