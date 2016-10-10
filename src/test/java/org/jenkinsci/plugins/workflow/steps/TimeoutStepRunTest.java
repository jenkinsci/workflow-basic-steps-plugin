package org.jenkinsci.plugins.workflow.steps;

import hudson.model.Result;
import jenkins.model.CauseOfInterruption;
import jenkins.model.InterruptedBuildAction;

import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.TimeoutInfoAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable.Row;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.*;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.util.List;
import org.jenkinsci.plugins.workflow.steps.SleepStep;
import org.jenkinsci.plugins.workflow.steps.TimeoutStepExecution;

/**
 * @author Kohsuke Kawaguchi
 */
public class TimeoutStepRunTest extends Assert {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

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
                        "node { timeout(time:5, unit:'SECONDS') { sleep 10; echo 'NotHere' } }"));
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
                        + "}\n"));
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
                        + "}\n"));
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
                        + "}\n"));
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
                assertNotNull(action);
                List<CauseOfInterruption> causes = action.getCauses();
                assertEquals(1, causes.size());
                assertEquals(TimeoutStepExecution.ExceededTimeout.class, causes.get(0).getClass());
                story.j.assertBuildStatus(Result.ABORTED, run);
            }
        });
    }

    @Test
    public void elastic() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  timeout(id: 'main', time: 20, unit: 'SECONDS', elastic: 1.0) {\n"
                        + "    assert('20000' == \"${env.timeout}\");\n"
                        + "    sleep 5;\n"
                        + "  }\n"
                        + "}\n"
                ));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0).get());
                TimeoutInfoAction info = TimeoutStepExecution.extractTimeoutInfoActionWithId("main", b);
                assertNotNull(info);
                assertThat(info.getDuration(), Matchers.greaterThanOrEqualTo(5000L));
                assertThat(info.getDuration(), Matchers.lessThan(20000L));

                p.setDefinition(new CpsFlowDefinition(String.format(""
                    + "node {\n"
                    + "  timeout(id: 'main', time:20, unit: 'SECONDS', elastic: 1.0) {\n"
                    + "    assert('%d' == \"${env.timeout}\");\n"
                    + "    sleep 10;\n"
                    + "  }\n"
                    + "}", info.getDuration()
                )));
                story.j.assertBuildStatus(Result.ABORTED, p.scheduleBuild2(0).get());
            }
        });
    }

    @Test
    public void elasticWithTwoBlocks() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  timeout(id: 'main1', time: 20, unit: 'SECONDS', elastic: 1.0) {\n"
                        + "    assert('20000' == \"${env.timeout}\");\n"
                        + "    sleep 5;\n"
                        + "  }\n"
                        + "  timeout(id: 'main2', time: 20, unit: 'SECONDS', elastic: 1.0) {\n"
                        + "    assert('20000' == \"${env.timeout}\");\n"
                        + "    sleep 3;\n"
                        + "  }\n"
                        + "}\n"
                ));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0).get());
                TimeoutInfoAction info1 = TimeoutStepExecution.extractTimeoutInfoActionWithId("main1", b);
                assertNotNull(info1);
                TimeoutInfoAction info2 = TimeoutStepExecution.extractTimeoutInfoActionWithId("main2", b);
                assertNotNull(info2);

                p.setDefinition(new CpsFlowDefinition(String.format(""
                    + "node {\n"
                    + "  timeout(id: 'main1', time: 20, unit: 'SECONDS', elastic: 1.0) {\n"
                    + "    assert('%d' == \"${env.timeout}\");\n"
                    + "    sleep 3;\n"
                    + "  }\n"
                    + "  timeout(id: 'main2', time: 20, unit: 'SECONDS', elastic: 1.0) {\n"
                    + "    assert('%d' == \"${env.timeout}\");\n"
                    + "    sleep 1;\n"
                    + "  }\n"
                    + "}\n",
                    info1.getDuration(),
                    info2.getDuration()
                )));
                story.j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0).get());
            }
        });
    }

    @Test
    public void elasticScaling() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  timeout(id: 'main', time: 20, unit: 'SECONDS', elastic: 1.5) {\n"
                        + "    assert('20000' == \"${env.timeout}\");\n"
                        + "    sleep 5;\n"
                        + "  }\n"
                        + "}\n"
                ));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0).get());
                TimeoutInfoAction info = TimeoutStepExecution.extractTimeoutInfoActionWithId("main", b);
                assertNotNull(info);
                assertThat(info.getDuration(), Matchers.greaterThanOrEqualTo(5000L));
                assertThat(info.getDuration(), Matchers.lessThan(20000L));

                p.setDefinition(new CpsFlowDefinition(String.format(""
                    + "node {\n"
                    + "  timeout(id: 'main', time:20, unit: 'SECONDS', elastic: 1.5) {\n"
                    + "    assert('%d' == \"${env.timeout}\");\n"
                    + "    sleep 5;\n"
                    + "  }\n"
                    + "}", (long)(1.5f * info.getDuration())
                )));
                story.j.assertBuildStatusSuccess(p.scheduleBuild2(0).get());
            }
        });
    }

    @Test
    public void elasticWithoutId() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  timeout(time: 20, unit: 'SECONDS', elastic: 1.5) {\n"
                        + "    assert('20000' == \"${env.timeout}\");\n"
                        + "    sleep 5;\n"
                        + "  }\n"
                        + "}\n"
                ));
                story.j.assertBuildStatusSuccess(p.scheduleBuild2(0).get());
                story.j.assertBuildStatusSuccess(p.scheduleBuild2(0).get());
            }
        });
    }

    @Test
    public void elasticIdChanged() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  timeout(id: 'main1', time: 20, unit: 'SECONDS', elastic: 1.5) {\n"
                        + "    assert('20000' == \"${env.timeout}\");\n"
                        + "    sleep 5;\n"
                        + "  }\n"
                        + "}\n"
                ));
                story.j.assertBuildStatusSuccess(p.scheduleBuild2(0).get());

                p.setDefinition(new CpsFlowDefinition(""
                        + "node {\n"
                        + "  timeout(id: 'main2', time: 20, unit: 'SECONDS', elastic: 1.5) {\n"
                        + "    assert('20000' == \"${env.timeout}\");\n"
                        + "    sleep 5;\n"
                        + "  }\n"
                        + "}\n"
                ));
                story.j.assertBuildStatusSuccess(p.scheduleBuild2(0).get());
            }
        });
    }

    // TODO: timeout inside parallel

}
