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
import jenkins.model.CauseOfInterruption;
import jenkins.model.InterruptedBuildAction;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.junit.jupiter.WithGitSampleRepo;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;

import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable.Row;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.DataBoundConstructor;

@WithGitSampleRepo
class TimeoutStepTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();
    @RegisterExtension
    private final JenkinsSessionExtension sessions = new JenkinsSessionExtension();

    private GitSampleRepoRule git;

    @BeforeEach
    void beforeEach(GitSampleRepoRule repo) {
        git = repo;
    }

    @Test
    void configRoundTrip() throws Throwable {
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
    void basic() throws Throwable {
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
    void basicWithBlock() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "node { timeout(time:5, unit:'SECONDS') { withEnv([]) { sleep 7; echo 'NotHere' } } }", true));
                WorkflowRun b = j.buildAndAssertStatus(Result.ABORTED, p);
                j.assertLogNotContains("NotHere", b);
        });
    }

    @Test
    void killingParallel() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("""
                        node {
                          timeout(time:5, unit:'SECONDS') {
                            parallel(
                              a: { echo 'ShouldBeHere1'; sleep 10; echo 'NotHere' },
                              b: { echo 'ShouldBeHere2'; sleep 10; echo 'NotHere' },
                            );
                            echo 'NotHere'
                          }
                          echo 'NotHere'
                        }
                        """, true));
                WorkflowRun b = j.buildAndAssertStatus(Result.ABORTED, p);

                // make sure things that are supposed to run do, and things that are NOT supposed to run do not.
                j.assertLogNotContains("NotHere", b);
                j.assertLogContains("ShouldBeHere1", b);
                j.assertLogContains("ShouldBeHere2", b);

                // we expect every sleep step to have failed
                FlowGraphTable t = new FlowGraphTable(b.getExecution());
                t.build();
                for (Row r : t.getRows()) {
                    if (r.getNode() instanceof StepAtomNode a) {
                        if (a.getDescriptor().getClass() == SleepStep.DescriptorImpl.class) {
                            assertNotNull(a.getAction(ErrorAction.class));
                        }
                    }
                }
        });
    }

    @Issue("JENKINS-26521")
    @Test
    void activity() throws Throwable {
        assumeTrue(System.getenv("CI") == null, "TODO consistently failing in ci.jenkins.io yet passing locally");
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("""
                        node {
                          timeout(time:5, unit:'SECONDS', activity: true) {
                            echo 'NotHere';
                            sleep 3;
                            echo 'NotHereYet';
                            sleep 3;
                            echo 'JustHere!';
                            sleep 10;
                            echo 'ShouldNot!';
                          }
                        }
                        """, true));
                WorkflowRun b = j.buildAndAssertStatus(Result.ABORTED, p);
                j.assertLogContains("JustHere!", b);
                j.assertLogNotContains("ShouldNot!", b);
        });
    }

    @Issue("JENKINS-26521")
    @Test
    void activityInParallel() throws Throwable {
        assumeTrue(System.getenv("CI") == null, "TODO also flaky in ci.jenkins.io");
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("""
                        node {
                          parallel(
                            a: {
                              timeout(time:5, unit:'SECONDS', activity: true) {
                                echo 'NotHere';
                                sleep 3;
                                echo 'NotHereYet';
                                sleep 3;
                                echo 'JustHere!';
                                sleep 10;
                                echo 'ShouldNot!';
                              }
                            },
                            b: {
                              for (int i = 0; i < 5; i++) {
                                echo 'Other Thread'
                                sleep 3
                              }
                            })
                        }
                        """, true));
                WorkflowRun b = j.buildAndAssertStatus(Result.ABORTED, p);
                j.assertLogContains("JustHere!", b);
                j.assertLogNotContains("ShouldNot!", b);
        });
    }

    @Issue("JENKINS-26521")
    @Test
    void activityRestart() throws Throwable {
        assumeTrue(System.getenv("CI") == null, "TODO also flaky in ci.jenkins.io");
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "restarted");
                p.setDefinition(new CpsFlowDefinition("""
                        node {
                          timeout(time:15, unit:'SECONDS', activity: true) {
                            echo 'NotHere';
                            semaphore 'restarted'
                            echo 'NotHereYet';
                            sleep 10;
                            echo 'NotHereYet';
                            sleep 10;
                            echo 'JustHere!';
                            sleep 30;
                            echo 'ShouldNot!';
                          }
                        }
                        """, true));
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("restarted/1", b);
        });
        sessions.then(j -> {
                WorkflowJob p = j.jenkins.getItemByFullName("restarted", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                assertTrue(b.isBuilding(), "took more than 15s to restart?");
                SemaphoreStep.success("restarted/1", null);
                j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(b));
                j.assertLogContains("JustHere!", b);
                j.assertLogNotContains("ShouldNot!", b);
        });
    }

    @Test
    void activityRemote() throws Throwable {
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
    @Test
    void activityGit() throws Throwable {
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
    void restarted() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "restarted");
                p.setDefinition(new CpsFlowDefinition("""
                        node {
                          timeout(time: 15, unit: 'SECONDS') {
                            semaphore 'restarted'
                            sleep 999
                          }
                        }
                        """, true));
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("restarted/1", b);
        });
        sessions.then(j -> {
                WorkflowJob p = j.jenkins.getItemByFullName("restarted", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                assertTrue(b.isBuilding(), "took more than 15s to restart?");
                SemaphoreStep.success("restarted/1", null);
                j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(b));
        });
    }

    @Test
    void timeIsConsumed() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "timeIsConsumed");
                p.setDefinition(new CpsFlowDefinition("""
                        node {
                          timeout(time: 20, unit: 'SECONDS') {
                            sleep 10
                            semaphore 'timeIsConsumed'
                            sleep 10
                          }
                        }
                        """, true));
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("timeIsConsumed/1", b);
        });
        sessions.then(j -> {
                WorkflowJob p = j.jenkins.getItemByFullName("timeIsConsumed", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                SemaphoreStep.success("timeIsConsumed/1", null);
                WorkflowRun run = j.waitForCompletion(b);
                InterruptedBuildAction action = b.getAction(InterruptedBuildAction.class);
                assumeTrue(action != null, "TODO sometimes flakes");
                List<CauseOfInterruption> causes = action.getCauses();
                assertEquals(1, causes.size());
                assertEquals(TimeoutStepExecution.ExceededTimeout.class, causes.get(0).getClass());
                j.assertBuildStatus(Result.ABORTED, run);
        });
    }

    @Issue("JENKINS-39072")
    @Test
    void unresponsiveBody() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("timeout(time: 2, unit: 'SECONDS') {unkillable()}", true));
                j.buildAndAssertStatus(Result.ABORTED, p);
        });
    }

    @SuppressWarnings("unused")
    public static class UnkillableStep extends Step {

        @DataBoundConstructor
        public UnkillableStep() {}

        @Override
        public StepExecution start(StepContext context) throws Exception {
            return new Execution(context);
        }

        private static class Execution extends StepExecution {

            private Execution(StepContext context) {
                super(context);
            }

            @Override
            public boolean start() throws Exception {
                return false;
            }

            @Override
            public void stop(@NonNull Throwable cause) throws Exception {
                getContext().get(TaskListener.class).getLogger().println("ignoring " + cause);
            }
        }

        @SuppressWarnings("unused")
        @TestExtension({"unresponsiveBody", "gracePeriod", "noImmediateForcibleTerminationOnResume", "nestingDetection"})
        public static class DescriptorImpl extends StepDescriptor {
            @Override
            public String getFunctionName() {
                return "unkillable";
            }
            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Collections.singleton(TaskListener.class);
            }
        }
    }

    @Disabled("TODO cannot find any way to solve this case")
    @Issue("JENKINS-39072")
    @Test
    void veryUnresponsiveBody() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("timeout(time: 2, unit: 'SECONDS') {while (true) {try {sleep 10} catch (e) {echo(/ignoring ${e}/)}}}", true));
                j.buildAndAssertStatus(Result.ABORTED, p);
        });
    }

    // TODO: timeout inside parallel

    @Issue("JENKINS-39134")
    @LocalData
    @Test
    void serialForm() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.jenkins.getItemByFullName("timeout", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                RunListener.fireStarted(b, TaskListener.NULL);
                j.assertBuildStatusSuccess(j.waitForCompletion(b));
        });
    }

    @Issue("JENKINS-54607")
    @Test
    void gracePeriod() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("timeout(time: 15, unit: 'SECONDS') {unkillable()}", true));
                j.buildAndAssertStatus(Result.ABORTED, p);
                assertThat(p.getLastBuild().getDuration(), lessThan(29_000L)); // 29 seconds
        });
    }

    @Issue("JENKINS-42940")
    @LocalData
    @Test
    void noImmediateForcibleTerminationOnResume() throws Throwable {
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
    void nestingDetection() throws Throwable {
        sessions.then(j -> {
            ScriptApproval.get().approveSignature("method org.jenkinsci.plugins.workflow.steps.FlowInterruptedException isActualInterruption");
            WorkflowJob p = j.createProject(WorkflowJob.class);

            // Inside
            p.setDefinition(
                    new CpsFlowDefinition(
                            """
                                    timeout(time: 5, unit: 'SECONDS') {
                                      try {
                                        sleep 10
                                      } catch (e) {
                                        echo e.isActualInterruption() ? 'GOOD' : 'BAD'
                                      }
                                    }""",
                            true));
            WorkflowRun b = j.buildAndAssertSuccess(p);
            j.assertLogContains("GOOD", b);
            j.assertLogNotContains("BAD", b);

            // Outside
            p.setDefinition(
                    new CpsFlowDefinition(
                            """
                                    try {
                                      timeout(time: 5, unit: 'SECONDS') {
                                        sleep 10
                                      }
                                    } catch (e) {
                                      echo e.isActualInterruption() ? 'BAD' : 'GOOD'
                                    }""",
                            true));
            b = j.buildAndAssertSuccess(p);
            j.assertLogContains("GOOD", b);
            j.assertLogNotContains("BAD", b);

            // Between
            p.setDefinition(
                    new CpsFlowDefinition(
                            """
                                    timeout(time: 5, unit: 'SECONDS') {
                                      try {
                                        timeout(time: 20, unit: 'SECONDS') {
                                          sleep 10
                                        }
                                      } catch (e) {
                                        echo e.isActualInterruption() ? 'GOOD' : 'BAD'
                                      }
                                    }""",
                            true));
            b = j.buildAndAssertSuccess(p);
            j.assertLogContains("GOOD", b);
            j.assertLogNotContains("BAD", b);

            // Inside (unkillable)
            p.setDefinition(
                    new CpsFlowDefinition(
                            """
                                    timeout(time: 5, unit: 'SECONDS') {
                                      try {
                                        unkillable()
                                      } catch (e) {
                                        echo e.isActualInterruption() ? 'GOOD' : 'BAD'
                                      }
                                    }""",
                            true));
            b = p.scheduleBuild2(0).get();
            j.assertLogContains("GOOD", b);
            j.assertLogNotContains("BAD", b);

            // Outside (unkillable)
            p.setDefinition(
                    new CpsFlowDefinition(
                            """
                                    try {
                                      timeout(time: 5, unit: 'SECONDS') {
                                        unkillable()
                                      }
                                    } catch (e) {
                                      echo e.isActualInterruption() ? 'BAD' : 'GOOD'\
                                    }""",
                            true));
            b = j.buildAndAssertSuccess(p);
            j.assertLogContains("GOOD", b);
            j.assertLogNotContains("BAD", b);

            // Between (unkillable)
            p.setDefinition(
                    new CpsFlowDefinition(
                            """
                                    timeout(time: 5, unit: 'SECONDS') {
                                      try {
                                        timeout(time: 20, unit: 'SECONDS') {
                                          unkillable()
                                        }
                                      } catch (e) {
                                        echo e.isActualInterruption() ? 'GOOD' : 'BAD'
                                      }
                                    }""",
                            true));
            b = j.buildAndAssertSuccess(p);
            j.assertLogContains("GOOD", b);
            j.assertLogNotContains("BAD", b);
        });
    }
}
