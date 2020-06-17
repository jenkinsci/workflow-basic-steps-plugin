package org.jenkinsci.plugins.workflow.steps;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests {@link DebugStep}.
 */
public class DebugStepTest {

	@ClassRule
	public static BuildWatcher buildWatcher = new BuildWatcher();
	@Rule
	public JenkinsRule r = new JenkinsRule();

	public static final String DEBUG_BLOCK_EXECUTED = "DEBUG_BLOCK_EXECUTED";

	@Test
	public void debugEnabledExecuteTheBlock() throws Exception {
		WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
		p.setDefinition(new CpsFlowDefinition("debug(true) {\n" + "    println '" + DEBUG_BLOCK_EXECUTED + "'\n" + "}\n" + "println 'Done!'", true));

		WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));

		r.assertLogContains(DEBUG_BLOCK_EXECUTED, b);
	}

	@Test
	public void debugDisabledNotExecuteTheBlock() throws Exception {
		WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
		p.setDefinition(new CpsFlowDefinition("debug(false) {\n" + "    println '" + DEBUG_BLOCK_EXECUTED + "'\n" + "}\n" + "println 'Done!'", true));

		WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));

		r.assertLogNotContains(DEBUG_BLOCK_EXECUTED, b);
	}

	@Test
	public void missingParameterBreaksTheBuild() throws Exception {
		WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
		p.setDefinition(new CpsFlowDefinition("debug {\n" + "    println '" + DEBUG_BLOCK_EXECUTED + "'\n" + "}\n" + "println 'Done!'", true));

		WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
	}

	@Test
	public void missingBlockBreaksTheBuild() throws Exception {
		WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
		p.setDefinition(new CpsFlowDefinition("debug(true)\n" + "println 'Done!'", true));

		WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));

		r.assertLogContains("no body to invoke", b);
	}

}
