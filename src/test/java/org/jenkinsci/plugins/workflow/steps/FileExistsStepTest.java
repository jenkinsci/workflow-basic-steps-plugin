package org.jenkinsci.plugins.workflow.steps;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsSessionRule;

public class FileExistsStepTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public JenkinsSessionRule sessions = new JenkinsSessionRule();

    @Issue("JENKINS-48138")
    @Test
    public void emptyStringWarning() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("node { fileExists('') }", true));
                j.assertLogContains(Messages.FileExistsStep_EmptyString(), j.buildAndAssertSuccess(p));
        });
    }

    @Test
    public void nullStringWarning() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("node { fileExists(null) }", true));
                j.assertLogContains(Messages.FileExistsStep_EmptyString(), j.buildAndAssertSuccess(p));
        });
    }
}
