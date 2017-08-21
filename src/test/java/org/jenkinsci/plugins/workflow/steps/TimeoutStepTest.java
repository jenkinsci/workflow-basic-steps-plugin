/*
 * The MIT License
 *
 * Copyright 2014-2016 CloudBees, Inc.
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
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import jenkins.model.CauseOfInterruption;
import jenkins.model.InterruptedBuildAction;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable.Row;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.*;
import static org.junit.Assert.assertEquals;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.DataBoundConstructor;

public class TimeoutStepTest extends Assert {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test public void configRoundTrip() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                TimeoutStep s1 = new TimeoutStep(3);
                s1.setUnit(TimeUnit.HOURS);
                TimeoutStep s2 = new StepConfigTester(story.j).configRoundTrip(s1);
                story.j.assertEqualDataBoundBeans(s1, s2);
            }
        });
    }

    /**
     * The simplest possible timeout step ever.
     */
    @Test
    public void basic() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "node { timeout(time:5, unit:'SECONDS') { sleep 10; echo 'NotHere' } }", true));
                WorkflowRun b = story.j.assertBuildStatus(Result.ABORTED, p.scheduleBuild2(0).get());
                story.j.assertLogNotContains("NotHere", b);
            }
        });
    }

    @Issue("JENKINS-34637")
    @Test
    public void basicWithBlock() {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "node { timeout(time:5, unit:'SECONDS') { withEnv([]) { sleep 7; echo 'NotHere' } } }", true));
                WorkflowRun b = story.j.assertBuildStatus(Result.ABORTED, p.scheduleBuild2(0).get());
                story.j.assertLogNotContains("NotHere", b);
            }
        });
    }

    @Test
    public void killingParallel() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  timeout(time:5, unit:'SECONDS') {\n"
                        + "    parallel(\n"
                        + "      a: { echo 'ShouldBeHere1'; sleep 10; echo 'NotHere' },\n"
                        + "      b: { echo 'ShouldBeHere2'; sleep 10; echo 'NotHere' },\n"
                        + "    );\n"
                        + "    echo 'NotHere'\n"
                        + "  }\n"
                        + "  echo 'NotHere'\n"
                        + "}\n", true));
                WorkflowRun b = story.j.assertBuildStatus(Result.ABORTED, p.scheduleBuild2(0).get());

                // make sure things that are supposed to run do, and things that are NOT supposed to run do not.
                story.j.assertLogNotContains("NotHere", b);
                story.j.assertLogContains("ShouldBeHere1", b);
                story.j.assertLogContains("ShouldBeHere2", b);

                // we expect every sleep step to have failed
                FlowGraphTable t = new FlowGraphTable(b.getExecution());
                t.build();
                for (Row r : t.getRows()) {
                    if (r.getNode() instanceof StepAtomNode) {
                        StepAtomNode a = (StepAtomNode) r.getNode();
                        if (a.getDescriptor().getClass() == SleepStep.DescriptorImpl.class) {
                            assertTrue(a.getAction(ErrorAction.class) != null);
                        }
                    }
                }
            }
        });
    }

    @Issue("JENKINS-26163")
    @Test
    public void restarted() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "restarted");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  timeout(time: 15, unit: 'SECONDS') {\n"
                        + "    semaphore 'restarted'\n"
                        + "    sleep 999\n"
                        + "  }\n"
                        + "}\n", true));
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("restarted/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("restarted", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                assertTrue("took more than 15s to restart?", b.isBuilding());
                SemaphoreStep.success("restarted/1", null);
                story.j.assertBuildStatus(Result.ABORTED, story.j.waitForCompletion(b));
            }
        });
    }

    @Test
    public void timeIsConsumed() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "timeIsConsumed");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  timeout(time: 20, unit: 'SECONDS') {\n"
                        + "    sleep 10\n"
                        + "    semaphore 'timeIsConsumed'\n"
                        + "    sleep 10\n"
                        + "  }\n"
                        + "}\n", true));
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("timeIsConsumed/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("timeIsConsumed", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                SemaphoreStep.success("timeIsConsumed/1", null);
                WorkflowRun run = story.j.waitForCompletion(b);
                InterruptedBuildAction action = b.getAction(InterruptedBuildAction.class);
                assertNotNull(action); // TODO flaked on windows-8-2.32.3
                List<CauseOfInterruption> causes = action.getCauses();
                assertEquals(1, causes.size());
                assertEquals(TimeoutStepExecution.ExceededTimeout.class, causes.get(0).getClass());
                story.j.assertBuildStatus(Result.ABORTED, run);
            }
        });
    }

    @Issue("JENKINS-39072")
    @Test public void unresponsiveBody() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("timeout(time: 2, unit: 'SECONDS') {unkillable()}", true));
                story.j.assertBuildStatus(Result.ABORTED, p.scheduleBuild2(0).get());
            }
        });
    }
    public static class UnkillableStep extends Step {
        @DataBoundConstructor public UnkillableStep() {}
        @Override public StepExecution start(StepContext context) throws Exception {
            return new Execution(context);
        }
        private static class Execution extends StepExecution {
            private Execution(StepContext context) {
                super(context);
            }
            @Override public boolean start() throws Exception {
                return false;
            }
            @Override public void stop(Throwable cause) throws Exception {
                getContext().get(TaskListener.class).getLogger().println("ignoring " + cause);
            }
        }
        @TestExtension("unresponsiveBody") public static class DescriptorImpl extends StepDescriptor {
            @Override public String getFunctionName() {
                return "unkillable";
            }
            @Override public Set<? extends Class<?>> getRequiredContext() {
                return Collections.singleton(TaskListener.class);
            }
        }
    }

    @Ignore("TODO cannot find any way to solve this case")
    @Issue("JENKINS-39072")
    @Test public void veryUnresponsiveBody() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("timeout(time: 2, unit: 'SECONDS') {while (true) {try {sleep 10} catch (e) {echo(/ignoring ${e}/)}}}", true));
                story.j.assertBuildStatus(Result.ABORTED, p.scheduleBuild2(0).get());
            }
        });
    }

    // TODO: timeout inside parallel

    @Issue("JENKINS-39134")
    @LocalData
    @Test public void serialForm() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("timeout", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                RunListener.fireStarted(b, TaskListener.NULL);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
            }
        });
    }
}
