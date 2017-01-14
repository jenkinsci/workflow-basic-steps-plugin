/*
 * The MIT License
 *
 * Copyright 2017 Baptiste Mathus.
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

import hudson.AbortException;
import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RequirePluginsStepTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void allPluginsPresent() throws Exception {
        String requirement = "workflow-step-api workflow-job";
        requirePlugins(requirement, Result.SUCCESS);
    }


    @Test
    public void absentPlugin() throws Exception {
        try {
            requirePlugins("absent ,  workflow-step-api otherabsent", Result.FAILURE);
        } catch (AbortException e) {
            assertTrue(e.getMessage().contains("absent"));
            assertTrue(e.getMessage().contains("otherabsent"));
            assertFalse(e.getMessage().contains("workflow-step-api"));
        }
    }

    @Test
    public void versionOK() throws Exception {
        requirePlugins("junit,workflow-step-api@1.0 ", Result.SUCCESS);
    }

    @Test
    public void tooOld() throws Exception {
        try {
            requirePlugins(" workflow-step-api@9.5 ", Result.FAILURE);
        } catch (AbortException e) {
            assertTrue(e.getMessage().contains("workflow-step-api@9.5"));
        }
    }

    @Test
    public void funkySpec() throws Exception {
        requirePlugins(", junit,, workflow-step-api  workflow-step-api@1.0  ,", Result.SUCCESS);
    }

    private void requirePlugins(String requirement, Result result) throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("requirePlugins '" + requirement + "'", true));
        WorkflowRun b = r.assertBuildStatus(result, p.scheduleBuild2(0));
    }
}
