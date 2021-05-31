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

import java.util.logging.Level;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.FilePathDynamicContext;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.JenkinsSessionRule;

public class PushdStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsSessionRule sessions = new JenkinsSessionRule();
    @Rule public LoggerRule logging = new LoggerRule().record(FilePathDynamicContext.class, Level.FINE);

    @Test public void basics() throws Throwable {
        sessions.then(j -> {
            WorkflowJob p = j.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("node {dir('subdir') {echo(/now the pwd=${pwd()}/)}}", true));
            j.assertLogContains("now the pwd=" + j.jenkins.getWorkspaceFor(p).child("subdir"), j.buildAndAssertSuccess(p));
        });
    }

    @Test public void restarting() throws Throwable {
        sessions.then(j -> {
            WorkflowJob p = j.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("node {dir('subdir') {semaphore 'restarting'; echo(/now the pwd=${pwd()}/)}}", true));
            WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
            SemaphoreStep.waitForStart("restarting/1", b);
        });
        sessions.then(j -> {
            SemaphoreStep.success("restarting/1", null);
            WorkflowJob p = j.jenkins.getItemByFullName("p", WorkflowJob.class);
            WorkflowRun b = p.getLastBuild();
            j.assertLogContains("now the pwd=" + j.jenkins.getWorkspaceFor(p).child("subdir"), j.assertBuildStatusSuccess(j.waitForCompletion(b)));
        });
    }

}
