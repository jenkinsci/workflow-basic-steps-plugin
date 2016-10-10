/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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

/**
 * @author Andrew Bayer
 */
public class MatrixStepTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void runMatrix() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "workflow");

        job.setDefinition(new CpsFlowDefinition("matrix([foo: ['bar', 'baz'], fruit:['banana', 'apple', 'orange']]) {\n" +
                "echo \"foo+fruit: ${env.foo}+${env.fruit}\"; }", true));

        WorkflowRun run = r.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get());
        r.assertLogContains("foo+fruit: bar+banana", run);
        r.assertLogContains("foo+fruit: bar+apple", run);
        r.assertLogContains("foo+fruit: bar+orange", run);
        r.assertLogContains("foo+fruit: baz+banana", run);
        r.assertLogContains("foo+fruit: baz+apple", run);
        r.assertLogContains("foo+fruit: baz+orange", run);
    }

}
