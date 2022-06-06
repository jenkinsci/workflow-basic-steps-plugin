/*
 * The MIT License
 *
 * Copyright 2022 CloudBees, Inc.
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

import hudson.Functions;
import hudson.model.Slave;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.durabletask.FileMonitoringTask;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.ErrorCondition;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionList;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.durable_task.DurableTaskStep;
import org.jenkinsci.plugins.workflow.support.steps.AgentErrorCondition;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.InboundAgentRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsSessionRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Tests of retrying {@code node} blocks which should probably be moved to {@code workflow-durable-task-step} when feasible.
 */
public class RetryExecutorStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsSessionRule sessions = new JenkinsSessionRule();
    @Rule public InboundAgentRule inboundAgents = new InboundAgentRule();
    @Rule public LoggerRule logging = new LoggerRule();

    @Issue("JENKINS-49707")
    @Test public void retryNodeBlock() throws Throwable {
        Assume.assumeFalse("TODO corresponding batch script TBD", Functions.isWindows());
        sessions.then(r -> {
            logging.record(DurableTaskStep.class, Level.FINE).record(FileMonitoringTask.class, Level.FINE).record(ExecutorStepExecution.class, Level.FINE);
            Slave s = inboundAgents.createAgent(r, "dumbo1");
            s.setLabelString("dumb");
            r.jenkins.updateNode(s); // to force setLabelString to be honored
            WorkflowJob p = r.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                "retry(count: 2, conditions: [custom()]) {\n" +
                "  node('dumb') {\n" +
                "    sh 'sleep 10'\n" +
                "  }\n" +
                "}", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            r.waitForMessage("+ sleep", b);
            inboundAgents.stop("dumbo1");
            r.jenkins.removeNode(s);
            r.waitForMessage(RetryThis.MESSAGE, b);
            s = inboundAgents.createAgent(r, "dumbo2");
            s.setLabelString("dumb");
            r.jenkins.updateNode(s);
            r.waitForMessage("Running on dumbo2 in ", b);
            r.assertBuildStatusSuccess(r.waitForCompletion(b));
        });
    }

    @Issue("JENKINS-49707")
    @Test public void retryNodeBlockSynch() throws Throwable {
        Assume.assumeFalse("TODO corresponding Windows process TBD", Functions.isWindows());
        sessions.then(r -> {
            logging.record(ExecutorStepExecution.class, Level.FINE);
            Slave s = inboundAgents.createAgent(r, "dumbo1");
            s.setLabelString("dumb");
            r.jenkins.updateNode(s);
            WorkflowJob p = r.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                "retry(count: 2, conditions: [custom()]) {\n" +
                "  node('dumb') {\n" +
                "    hang()\n" +
                "  }\n" +
                "}", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            r.waitForMessage("$ sleep", b);
            // Immediate kill causes RequestAbortedException from RemoteLauncher.launch, which passes test;
            // but more realistic to see IOException: Backing channel 'JNLP4-connect connection from …' is disconnected.
            // from RemoteLauncher$ProcImpl.isAlive via RemoteInvocationHandler.channelOrFail.
            // Either way the top-level exception wraps ClosedChannelException:
            Thread.sleep(1000);
            inboundAgents.stop("dumbo1");
            r.jenkins.removeNode(s);
            r.waitForMessage(RetryThis.MESSAGE, b);
            s = inboundAgents.createAgent(r, "dumbo2");
            s.setLabelString("dumb");
            r.jenkins.updateNode(s);
            r.waitForMessage("Running on dumbo2 in ", b);
            r.assertBuildStatusSuccess(r.waitForCompletion(b));
        });
    }
    public static final class HangStep extends Step {
        @DataBoundConstructor public HangStep() {}
        @Override public StepExecution start(StepContext context) throws Exception {
            return StepExecutions.synchronousNonBlocking(context, c -> {
                c.get(hudson.Launcher.class).launch().cmds("sleep", "10").stdout(c.get(TaskListener.class)).start().join();
                return null;
            });
        }
        @TestExtension("retryNodeBlockSynch") public static final class DescriptorImpl extends StepDescriptor {
            @Override public String getFunctionName() {
                return "hang";
            }
            @Override public Set<? extends Class<?>> getRequiredContext() {
                return new HashSet<>(Arrays.asList(hudson.Launcher.class, TaskListener.class));
            }
        }
    }

    @Ignore("TODO pending https://github.com/jenkinsci/workflow-durable-task-step-plugin/pull/180")
    @Issue("JENKINS-49707")
    @Test public void retryNewStepAcrossRestarts() throws Throwable {
        logging.record(DurableTaskStep.class, Level.FINE).record(FileMonitoringTask.class, Level.FINE).record(ExecutorStepExecution.class, Level.FINE);
        sessions.then(r -> {
            Slave s = inboundAgents.createAgent(r, "dumbo1");
            s.setLabelString("dumb");
            r.jenkins.updateNode(s);
            WorkflowJob p = r.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                "retry(count: 2, conditions: [custom()]) {\n" +
                "  node('dumb') {\n" +
                "    semaphore 'wait'\n" +
                "    isUnix()\n" +
                "  }\n" +
                "}", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b);
        });
        sessions.then(r -> {
            inboundAgents.stop("dumbo1");
            r.jenkins.removeNode(r.jenkins.getNode("dumbo1"));
            SemaphoreStep.success("wait/1", null);
            SemaphoreStep.success("wait/2", null);
            WorkflowRun b = r.jenkins.getItemByFullName("p", WorkflowJob.class).getBuildByNumber(1);
            r.waitForMessage(RetryThis.MESSAGE, b);
            Slave s = inboundAgents.createAgent(r, "dumbo2");
            s.setLabelString("dumb");
            r.jenkins.updateNode(s);
            r.waitForMessage("Running on dumbo2 in ", b);
            r.assertBuildStatusSuccess(r.waitForCompletion(b));
        });
    }

    @Ignore("TODO pending https://github.com/jenkinsci/workflow-durable-task-step-plugin/pull/180")
    @Issue({"JENKINS-49707", "JENKINS-30383"})
    @Test public void retryNodeBlockSynchAcrossRestarts() throws Throwable {
        logging.record(ExecutorStepExecution.class, Level.FINE).record(FlowExecutionList.class, Level.FINE);
        sessions.then(r -> {
            Slave s = inboundAgents.createAgent(r, "dumbo1");
            s.setLabelString("dumb");
            r.jenkins.updateNode(s);
            WorkflowJob p = r.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                "retry(count: 2, conditions: [custom()]) {\n" +
                "  node('dumb') {\n" +
                "    waitWithoutAgent()\n" +
                "  }\n" +
                "}", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            r.waitForMessage("Sleeping without agent", b);
        });
        sessions.then(r -> {
            inboundAgents.stop("dumbo1");
            r.jenkins.removeNode(r.jenkins.getNode("dumbo1"));
            WorkflowRun b = r.jenkins.getItemByFullName("p", WorkflowJob.class).getBuildByNumber(1);
            r.waitForMessage(RetryThis.MESSAGE, b);
            Slave s = inboundAgents.createAgent(r, "dumbo2");
            s.setLabelString("dumb");
            r.jenkins.updateNode(s);
            r.waitForMessage("Running on dumbo2 in ", b);
            r.assertBuildStatusSuccess(r.waitForCompletion(b));
        });
    }
    public static final class WaitWithoutAgentStep extends Step {
        @DataBoundConstructor public WaitWithoutAgentStep() {}
        @Override public StepExecution start(StepContext context) throws Exception {
            return StepExecutions.synchronousNonBlocking(context, c -> {
                c.get(TaskListener.class).getLogger().println("Sleeping without agent");
                Jenkins j = Jenkins.get();
                Thread.sleep(10_000);
                if (Jenkins.get() != j) {
                    // Finished sleeping in another session, which outside of JenkinsSessionRule is impossible.
                    // Avoid marking the step as completed since the Java objects here are stale.
                    // See https://github.com/jenkinsci/workflow-cps-plugin/pull/540.
                    Thread.sleep(Long.MAX_VALUE);
                }
                return null;
            });
        }
        @TestExtension("retryNodeBlockSynchAcrossRestarts") public static final class DescriptorImpl extends StepDescriptor {
            @Override public String getFunctionName() {
                return "waitWithoutAgent";
            }
            @Override public Set<? extends Class<?>> getRequiredContext() {
                return Collections.singleton(TaskListener.class);
            }
        }
    }

    public static final class RetryThis extends ErrorCondition {
        private static final String MESSAGE = "Satisfactory retry condition";
        @DataBoundConstructor public RetryThis() {}
        @Override public boolean test(Throwable t, StepContext context) throws IOException, InterruptedException {
            TaskListener listener = context.get(TaskListener.class);
            Functions.printStackTrace(t, listener.getLogger());
            if (new AgentErrorCondition().test(t, context) || new SynchronousResumeNotSupportedErrorCondition().test(t, context)) {
                listener.getLogger().println(MESSAGE);
                return true;
            } else {
                listener.getLogger().println("Ignoring " + t);
                return false;
            }
        }
        @Symbol("custom")
        @TestExtension public static final class DescriptorImpl extends ErrorConditionDescriptor {}
    }

}
