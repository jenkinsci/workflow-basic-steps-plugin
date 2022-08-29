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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Functions;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import jenkins.model.CauseOfInterruption;
import jenkins.model.InterruptedBuildAction;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable.Row;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsSessionRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.DataBoundConstructor;

public class TimeoutStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule public JenkinsSessionRule sessions = new JenkinsSessionRule();

    @Rule public GitSampleRepoRule git = new GitSampleRepoRule();

    @Rule public LoggerRule logging = new LoggerRule().record(TimeoutStepExecution.class, Level.FINE);

    @Test public void configRoundTrip() throws Throwable {
        sessions.then(j -> {
                TimeoutStep s1 = new TimeoutStep(3);
                s1.setUnit(TimeUnit.HOURS);
                TimeoutStep s2 = new StepConfigTester(j).configRoundTrip(s1);
                j.assertEqualDataBoundBeans(s1, s2);
        });
    }

    /**
     * The simplest possible timeout step ever.
     */
    @Test
    public void basic() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "node { timeout(time:5, unit:'SECONDS') { sleep 10; echo 'NotHere' } }", true));
                WorkflowRun b = j.buildAndAssertStatus(Result.ABORTED, p);
                j.assertLogNotContains("NotHere", b);
        });
    }

    @Issue("JENKINS-34637")
    @Test
    public void basicWithBlock() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "node { timeout(time:5, unit:'SECONDS') { withEnv([]) { sleep 7; echo 'NotHere' } } }", true));
                WorkflowRun b = j.buildAndAssertStatus(Result.ABORTED, p);
                j.assertLogNotContains("NotHere", b);
        });
    }

    @Test
    public void killingParallel() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
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
                WorkflowRun b = j.buildAndAssertStatus(Result.ABORTED, p);

                // make sure things that are supposed to run do, and things that are NOT supposed to run do not.
                j.assertLogNotContains("NotHere", b);
                j.assertLogContains("ShouldBeHere1", b);
                j.assertLogContains("ShouldBeHere2", b);

                // we expect every sleep step to have failed
                FlowGraphTable t = new FlowGraphTable(b.getExecution());
                t.build();
                for (Row r : t.getRows()) {
                    if (r.getNode() instanceof StepAtomNode) {
                        StepAtomNode a = (StepAtomNode) r.getNode();
                        if (a.getDescriptor().getClass() == SleepStep.DescriptorImpl.class) {
                            assertNotNull(a.getAction(ErrorAction.class));
                        }
                    }
                }
        });
    }

    @Issue("JENKINS-26521")
    @Test
    public void activity() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  timeout(time:5, unit:'SECONDS', activity: true) {\n"
                        + "    echo 'NotHere';\n"
                        + "    sleep 3;\n"
                        + "    echo 'NotHereYet';\n"
                        + "    sleep 3;\n"
                        + "    echo 'JustHere!';\n"
                        + "    sleep 10;\n"
                        + "    echo 'ShouldNot!';\n"
                        + "  }\n"
                        + "}\n", true));
                WorkflowRun b = j.buildAndAssertStatus(Result.ABORTED, p);
                j.assertLogContains("JustHere!", b);
                j.assertLogNotContains("ShouldNot!", b);
        });
    }

    @Issue("JENKINS-26521")
    @Test
    public void activityInParallel() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  parallel(\n"
                        + "    a: {\n"
                        + "      timeout(time:5, unit:'SECONDS', activity: true) {\n"
                        + "        echo 'NotHere';\n"
                        + "        sleep 3;\n"
                        + "        echo 'NotHereYet';\n"
                        + "        sleep 3;\n"
                        + "        echo 'JustHere!';\n"
                        + "        sleep 10;\n"
                        + "        echo 'ShouldNot!';\n"
                        + "      }\n"
                        + "    },\n"
                        + "    b: {\n"
                        + "      for (int i = 0; i < 5; i++) {\n"
                        + "        echo 'Other Thread'\n"
                        + "        sleep 3\n"
                        + "      }\n"
                        + "    })\n"
                        + "}\n", true));
                WorkflowRun b = j.buildAndAssertStatus(Result.ABORTED, p);
                j.assertLogContains("JustHere!", b);
                j.assertLogNotContains("ShouldNot!", b);
        });
    }

    @Issue("JENKINS-26521")
    @Test
    public void activityRestart() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "restarted");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  timeout(time:15, unit:'SECONDS', activity: true) {\n"
                        + "    echo 'NotHere';\n"
                        + "    semaphore 'restarted'\n"
                        + "    echo 'NotHereYet';\n"
                        + "    sleep 10;\n"
                        + "    echo 'NotHereYet';\n"
                        + "    sleep 10;\n"
                        + "    echo 'JustHere!';\n"
                        + "    sleep 20;\n"
                        + "    echo 'ShouldNot!';\n"
                        + "  }\n"
                        + "}\n", true));
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("restarted/1", b);
        });
        Thread.sleep(10_000); // restarting should count as activity
        sessions.then(j -> {
                WorkflowJob p = j.jenkins.getItemByFullName("restarted", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                assertTrue("took more than 15s to restart?", b.isBuilding());
                SemaphoreStep.success("restarted/1", null);
                j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(b));
                j.assertLogContains("JustHere!", b);
                j.assertLogNotContains("ShouldNot!", b);
        });
    }

    @Test
    public void activityRemote() throws Throwable {
        sessions.then(j -> {
            j.createSlave();
            WorkflowJob p = j.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("" +
                     "node('!master') {\n" +
                     "  timeout(time:5, unit:'SECONDS', activity: true) {\n" +
                    (Functions.isWindows() ?
                     "   bat '@echo off & echo NotHere && ping -n 3 127.0.0.1 >NUL && echo NotHereYet && ping -n 3 127.0.0.1 >NUL && echo JustHere && ping -n 20 127.0.0.1 >NUL && echo ShouldNot'\n" :
                     "   sh 'set +x; echo NotHere; sleep 3; echo NotHereYet; sleep 3; echo JustHere; sleep 10; echo ShouldNot'\n" ) +
                     "  }\n" +
                     "}\n", true));
            WorkflowRun b = j.assertBuildStatus(Result.ABORTED, p.scheduleBuild2(0));
            j.assertLogContains("JustHere", b);
            j.assertLogNotContains("ShouldNot", b);
        });
    }

    @Issue("JENKINS-54078")
    @Test public void activityGit() throws Throwable {
        sessions.then(j -> {
            j.createSlave();
            git.init();
            git.write("file", "content");
            git.git("commit", "--all", "--message=init");
            WorkflowJob p = j.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("" +
                     "node('!master') {\n" +
                     "  timeout(time: 5, unit: 'MINUTES', activity: true) {\n" +
                     "    git($/" + git + "/$)\n" +
                     "  }\n" +
                     "}\n", true));
            j.buildAndAssertSuccess(p);
        });
    }

    @Issue("JENKINS-26163")
    @Test
    public void restarted() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "restarted");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  timeout(time: 15, unit: 'SECONDS') {\n"
                        + "    semaphore 'restarted'\n"
                        + "    sleep 999\n"
                        + "  }\n"
                        + "}\n", true));
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("restarted/1", b);
        });
        sessions.then(j -> {
                WorkflowJob p = j.jenkins.getItemByFullName("restarted", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                assertTrue("took more than 15s to restart?", b.isBuilding());
                SemaphoreStep.success("restarted/1", null);
                j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(b));
        });
    }

    @Test
    public void timeIsConsumed() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "timeIsConsumed");
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
        });
        sessions.then(j -> {
                WorkflowJob p = j.jenkins.getItemByFullName("timeIsConsumed", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                SemaphoreStep.success("timeIsConsumed/1", null);
                WorkflowRun run = j.waitForCompletion(b);
                InterruptedBuildAction action = b.getAction(InterruptedBuildAction.class);
                assumeThat("TODO sometimes flakes", action, notNullValue());
                List<CauseOfInterruption> causes = action.getCauses();
                assertEquals(1, causes.size());
                assertEquals(TimeoutStepExecution.ExceededTimeout.class, causes.get(0).getClass());
                j.assertBuildStatus(Result.ABORTED, run);
        });
    }

    @Issue("JENKINS-39072")
    @Test public void unresponsiveBody() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("timeout(time: 2, unit: 'SECONDS') {unkillable()}", true));
                j.buildAndAssertStatus(Result.ABORTED, p);
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
            @Override public void stop(@NonNull Throwable cause) throws Exception {
                getContext().get(TaskListener.class).getLogger().println("ignoring " + cause);
            }
        }
        @TestExtension({"unresponsiveBody", "gracePeriod", "noImmediateForcibleTerminationOnResume", "nestingDetection"}) public static class DescriptorImpl extends StepDescriptor {
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
    @Test public void veryUnresponsiveBody() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("timeout(time: 2, unit: 'SECONDS') {while (true) {try {sleep 10} catch (e) {echo(/ignoring ${e}/)}}}", true));
                j.buildAndAssertStatus(Result.ABORTED, p);
        });
    }

    // TODO: timeout inside parallel

    @Issue("JENKINS-39134")
    @LocalData
    @Test public void serialForm() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.jenkins.getItemByFullName("timeout", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                RunListener.fireStarted(b, TaskListener.NULL);
                j.assertBuildStatusSuccess(j.waitForCompletion(b));
        });
    }

    @Issue("JENKINS-54607")
    @Test public void gracePeriod() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("timeout(time: 15, unit: 'SECONDS') {unkillable()}", true));
                j.buildAndAssertStatus(Result.ABORTED, p);
                assertThat(p.getLastBuild().getDuration(), lessThan(29_000L)); // 29 seconds
        });
    }

    @Issue("JENKINS-42940")
    @LocalData
    @Test public void noImmediateForcibleTerminationOnResume() throws Throwable {
        /* Source of the @LocalData for reference:
        sessions.then(j -> {
            WorkflowJob p = j.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                    "timeout(time: 1, unit: 'SECONDS') {\n" +
                    "  unkillable()\n" +
                    "}\n", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            j.waitForMessage("ignoring " + FlowInterruptedException.class.getName(), b);
            // Saved while TimeoutStepExecution.forcible was true, between the first cancel and the force cancel.
            // Required some poking around in internals to save TimeoutStepExecution in the right state, which is why
            // this test uses @LocalData instead of just running the build directly.
        });*/
        sessions.then(j -> {
            WorkflowJob p = j.jenkins.getItemByFullName("p", WorkflowJob.class);
            WorkflowRun b = p.getBuildByNumber(1);
            j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(b));
        });
    }

    @Test
    public void nestingDetection() throws Throwable {
        sessions.then(j -> {
            ScriptApproval.get().approveSignature("method org.jenkinsci.plugins.workflow.steps.FlowInterruptedException isActualInterruption");
            WorkflowJob p = j.createProject(WorkflowJob.class);

            // Inside
            p.setDefinition(
                    new CpsFlowDefinition(
                            "timeout(time: 5, unit: 'SECONDS') {\n"
                                    + "  try {\n"
                                    + "    sleep 10\n"
                                    + "  } catch (e) {\n"
                                    + "    echo e.isActualInterruption() ? 'GOOD' : 'BAD'\n"
                                    + "  }\n"
                                    + "}",
                            true));
            WorkflowRun b = j.buildAndAssertSuccess(p);
            j.assertLogContains("GOOD", b);
            j.assertLogNotContains("BAD", b);

            // Outside
            p.setDefinition(
                    new CpsFlowDefinition(
                            "try {\n"
                                    + "  timeout(time: 5, unit: 'SECONDS') {\n"
                                    + "    sleep 10\n"
                                    + "  }\n"
                                    + "} catch (e) {\n"
                                    + "  echo e.isActualInterruption() ? 'BAD' : 'GOOD'\n"
                                    + "}",
                            true));
            b = j.buildAndAssertSuccess(p);
            j.assertLogContains("GOOD", b);
            j.assertLogNotContains("BAD", b);

            // Between
            p.setDefinition(
                    new CpsFlowDefinition(
                            "timeout(time: 5, unit: 'SECONDS') {\n"
                                    + "  try {\n"
                                    + "    timeout(time: 20, unit: 'SECONDS') {\n"
                                    + "      sleep 10\n"
                                    + "    }\n"
                                    + "  } catch (e) {\n"
                                    + "    echo e.isActualInterruption() ? 'GOOD' : 'BAD'\n"
                                    + "  }\n"
                                    + "}",
                            true));
            b = j.buildAndAssertSuccess(p);
            j.assertLogContains("GOOD", b);
            j.assertLogNotContains("BAD", b);

            // Inside (unkillable)
            p.setDefinition(
                    new CpsFlowDefinition(
                            "timeout(time: 5, unit: 'SECONDS') {\n"
                                    + "  try {\n"
                                    + "    unkillable()\n"
                                    + "  } catch (e) {\n"
                                    + "    echo e.isActualInterruption() ? 'GOOD' : 'BAD'\n"
                                    + "  }\n"
                                    + "}",
                            true));
            b = p.scheduleBuild2(0).get();
            j.assertLogContains("GOOD", b);
            j.assertLogNotContains("BAD", b);

            // Outside (unkillable)
            p.setDefinition(
                    new CpsFlowDefinition(
                            "try {\n"
                                    + "  timeout(time: 5, unit: 'SECONDS') {\n"
                                    + "    unkillable()\n"
                                    + "  }\n"
                                    + "} catch (e) {\n"
                                    + "  echo e.isActualInterruption() ? 'BAD' : 'GOOD'"
                                    + "}",
                            true));
            b = j.buildAndAssertSuccess(p);
            j.assertLogContains("GOOD", b);
            j.assertLogNotContains("BAD", b);

            // Between (unkillable)
            p.setDefinition(
                    new CpsFlowDefinition(
                            "timeout(time: 5, unit: 'SECONDS') {\n"
                                    + "  try {\n"
                                    + "    timeout(time: 20, unit: 'SECONDS') {\n"
                                    + "      unkillable()\n"
                                    + "    }\n"
                                    + "  } catch (e) {\n"
                                    + "    echo e.isActualInterruption() ? 'GOOD' : 'BAD'\n"
                                    + "  }\n"
                                    + "}",
                            true));
            b = j.buildAndAssertSuccess(p);
            j.assertLogContains("GOOD", b);
            j.assertLogNotContains("BAD", b);
        });
    }
}
