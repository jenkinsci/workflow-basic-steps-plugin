/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class SetBuildInfoStepTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void setDescription() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        String description = "Modified Description";
        p.setDefinition(new CpsFlowDefinition("setBuildInfo(description('" + description + "'))", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertThat(b.getDescription(), equalTo(description));
    }

    @Test public void setDisplayName() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        String displayName = "Modified Display Name";
        p.setDefinition(new CpsFlowDefinition("setBuildInfo(displayName('" + displayName + "'))", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertThat(b.getDisplayName(), equalTo(displayName));
    }

    @Test public void setKeepLog() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        boolean keepLog = true;
        p.setDefinition(new CpsFlowDefinition("setBuildInfo(keepLog(" + keepLog + "))", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertThat(b.isKeepLog(), equalTo(keepLog));
    }

    @Test public void setResult() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        Result result = Result.ABORTED;
        p.setDefinition(new CpsFlowDefinition("setBuildInfo(result('" + result.toString() + "'))", true));
        WorkflowRun b = r.assertBuildStatus(Result.ABORTED, p.scheduleBuild2(0));
        assertThat(b.getResult(), equalTo(result));
    }

}
