/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Rule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class ErrorInfoStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule s = new RestartableJenkinsRule();

    @Issue("JENKINS-28119")
    @Test public void smokes() {
        s.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = s.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    "try {\n" +
                    "  parallel fine: {\n" +
                    "    semaphore 'fine'\n" +
                    "  }, broken: {\n" +
                    "    echo 'erroneous step'\n" +
                    "    semaphore 'breaking'\n" +
                    "  }\n" +
                    "} catch (e) {\n" +
                    "  def info = errorInfo(e)\n" +
                    "  semaphore 'caught'\n" +
                    "  currentBuild.result = info.result\n" +
                    "  echo \"caught an instance of ${info.error.getClass()}\"\n" +
                    "  echo info.stackTrace\n" +
                    "  echo \"browse to: ${info.logURL}\"\n" +
                    "}", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("fine/1", b);
                SemaphoreStep.failure("breaking/1", new AbortException("oops"));
                SemaphoreStep.success("fine/1", null);
                SemaphoreStep.waitForStart("caught/1", null);
            }
        });
        s.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                SemaphoreStep.success("caught/1", null);
                WorkflowJob p = s.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                s.j.assertBuildStatus(Result.FAILURE, s.j.waitForCompletion(b));
                s.j.waitForMessage("End of Pipeline", b); // TODO why does it sometimes cut off at "Resuming build"? probably because WorkflowRun.finish sets isBuilding() → false before flushing the log
                s.j.assertLogContains("caught an instance of class hudson.AbortException", b);
                s.j.assertLogContains("oops", b);
                s.j.assertLogNotContains("\tat ", b);
                String log = JenkinsRule.getLog(b);
                Matcher matcher = Pattern.compile("^browse to: (http.+)$", Pattern.MULTILINE).matcher(log);
                assertTrue(log, matcher.find());
                String text = s.j.createWebClient().getPage(new URL(matcher.group(1))).getWebResponse().getContentAsString();
                assertTrue(text, text.contains("erroneous step"));
            }
        });
    }

}
