/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

import com.google.common.base.Predicates;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.console.LineTransformationOutputStream;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.tasks.SimpleBuildWrapper;
import org.hamcrest.Matchers;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.NodeStepTypePredicate;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsSessionRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CoreWrapperStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsSessionRule sessions = new JenkinsSessionRule();

    @Test public void useWrapper() throws Throwable {
        sessions.then(j -> {
                new SnippetizerTester(j).assertRoundTrip(new CoreWrapperStep(new MockWrapper()), "mock {\n    // some block\n}");
                Map<String,String> slaveEnv = new HashMap<>();
                if (Functions.isWindows()) {
                    slaveEnv.put("PATH", "c:\\windows\\System32");
                } else {
                    slaveEnv.put("PATH", "/usr/bin:/bin");
                }
                slaveEnv.put("HOME", "/home/jenkins");
                createSpecialEnvSlave(j, "slave", "", slaveEnv);
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("node('slave') {mock {semaphore 'restarting'; echo \"groovy PATH=${env.PATH}:\"; " + (Functions.isWindows()
                        ? "bat 'echo shell PATH=%PATH%:'}}"
                        : "sh 'echo shell PATH=$PATH:'}}"), true));
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("restarting/1", b);
        });
        sessions.then(j -> {
                SemaphoreStep.success("restarting/1", null);
                WorkflowJob p = j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                j.assertBuildStatusSuccess(j.waitForCompletion(b));
                String expected = Functions.isWindows() ? "/home/jenkins/extra/bin;c:\\windows\\System32" : "/home/jenkins/extra/bin:/usr/bin:/bin";
                j.assertLogContains("groovy PATH=" + expected + ":", b);
                j.assertLogContains("shell PATH=" + expected + ":", b);
                j.assertLogContains("ran DisposerImpl", b);
                j.assertLogNotContains("CoreWrapperStep", b);
        });
    }
    public static class MockWrapper extends SimpleBuildWrapper {
        @DataBoundConstructor public MockWrapper() {}
        @Override public void setUp(Context context, Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
            assertNotNull(initialEnvironment.toString(), initialEnvironment.get("PATH"));
            context.env("EXTRA", "${HOME}/extra");
            context.env("PATH+EXTRA", "${EXTRA}/bin");
            context.setDisposer(new DisposerImpl());
        }
        private static final class DisposerImpl extends Disposer {
            private static final long serialVersionUID = 1;
            @Override public void tearDown(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
                listener.getLogger().println("ran DisposerImpl");
            }
        }
        @Symbol("mock")
        @TestExtension("useWrapper") public static class DescriptorImpl extends BuildWrapperDescriptor {
            @NonNull
            @Override public String getDisplayName() {
                return "MockWrapper";
            }
            @Override public boolean isApplicable(AbstractProject<?,?> item) {
                return true;
            }
        }
    }

    @Test public void envStickiness() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    "def show(which) {\n" +
                    "  echo \"groovy ${which} ${env.TESTVAR}\"\n" +
                    (Functions.isWindows() ? "bat \"echo shell ${which} %TESTVAR%\"\n" :
                    "  sh \"echo shell ${which} \\$TESTVAR\"\n") +
                    "}\n" +
                    "env.TESTVAR = 'initial'\n" +
                    "node {\n" +
                    "  wrap([$class: 'OneVarWrapper']) {\n" +
                    "    show 'before'\n" +
                    "    env.TESTVAR = 'edited'\n" +
                    "    show 'after'\n" +
                    "  }\n" +
                    "  show 'outside'\n" +
                    "}", true));
                WorkflowRun b = j.buildAndAssertSuccess(p);
                j.assertLogContains("received initial", b);
                j.assertLogContains("groovy before wrapped", b);
                j.assertLogContains("shell before wrapped", b);
                // Any custom values set via EnvActionImpl.setProperty will be “frozen” for the duration of the CoreWrapperStep,
                // because they are always overridden by contextual values.
                j.assertLogContains("groovy after wrapped", b);
                j.assertLogContains("shell after wrapped", b);
                j.assertLogContains("groovy outside edited", b);
                j.assertLogContains("shell outside edited", b);
        });
    }
    public static class OneVarWrapper extends SimpleBuildWrapper {
        @DataBoundConstructor public OneVarWrapper() {}
        @Override public void setUp(Context context, Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
            listener.getLogger().println("received " + initialEnvironment.get("TESTVAR"));
            context.env("TESTVAR", "wrapped");
        }
        @TestExtension("envStickiness") public static class DescriptorImpl extends BuildWrapperDescriptor {
            @NonNull
            @Override public String getDisplayName() {
                return "OneVarWrapper";
            }
            @Override public boolean isApplicable(AbstractProject<?,?> item) {
                return true;
            }
        }
    }

    @Issue("JENKINS-27392")
    @Test public void loggerDecorator() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("node {echo 'outside #1'; wrap([$class: 'WrapperWithLogger']) {echo 'inside the block'}; echo 'outside #2'}", true));
                WorkflowRun b = j.buildAndAssertSuccess(p);
                j.assertLogContains("outside #1", b);
                j.assertLogContains("outside #2", b);
                j.assertLogContains("INSIDE THE BLOCK", b);
        });
    }
    public static class WrapperWithLogger extends SimpleBuildWrapper {
        @DataBoundConstructor public WrapperWithLogger() {}
        @Override public void setUp(SimpleBuildWrapper.Context context, Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {}
        @Override public ConsoleLogFilter createLoggerDecorator(@NonNull Run<?,?> build) {
            return new UpcaseFilter();
        }
        private static class UpcaseFilter extends ConsoleLogFilter implements Serializable {
            private static final long serialVersionUID = 1;
            @SuppressWarnings("rawtypes") // inherited
            @Override public OutputStream decorateLogger(AbstractBuild _ignore, final OutputStream logger) throws IOException, InterruptedException {
                return new LineTransformationOutputStream() {
                    @Override protected void eol(byte[] b, int len) throws IOException {
                        logger.write(new String(b, 0, len).toUpperCase(Locale.ROOT).getBytes());
                    }
                };
            }
        }
        @TestExtension("loggerDecorator") public static class DescriptorImpl extends BuildWrapperDescriptor {
            @NonNull
            @Override public String getDisplayName() {
                return "WrapperWithLogger";
            }
            @Override public boolean isApplicable(AbstractProject<?,?> item) {
                return true;
            }
        }
    }

    @Issue("JENKINS-45101")
    @Test public void argumentsToString() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                     "    wrap([$class: 'AnsiColorBuildWrapper', colorMapName: 'xterm']) {}\n" +
                     "}", true));
                WorkflowRun b = j.buildAndAssertSuccess(p);
                List<FlowNode> coreStepNodes = new DepthFirstScanner().filteredNodes(
                        b.getExecution(),
                        Predicates.and(
                                new NodeStepTypePredicate("wrap"),
                                n -> n instanceof StepStartNode && !((StepStartNode) n).isBody()));
                assertThat(coreStepNodes, Matchers.hasSize(1));
                assertEquals("xterm", ArgumentsAction.getStepArgumentsAsString(coreStepNodes.get(0)));
        });
    }

    // TODO add @LocalData serialForm test proving compatibility with executions dating back to workflow 1.4.3 on 1.580.1

    // TODO add to jenkins-test-harness
    /**
     * Akin to {@link JenkinsRule#createSlave(String, String, EnvVars)} but allows {@link Computer#getEnvironment} to be controlled rather than directly modifying launchers.
     * @param env variables to override in {@link Computer#getEnvironment}; null values will get unset even if defined in the test environment
     * @see <a href="https://github.com/jenkinsci/jenkins/pull/1553/files#r23784822">explanation in core PR 1553</a>
     */
    public static Slave createSpecialEnvSlave(JenkinsRule rule, String nodeName, @CheckForNull String labels, Map<String,String> env) throws Exception {
        @SuppressWarnings("deprecation") // keep consistency with original signature rather than force the caller to pass in a TemporaryFolder rule
        File remoteFS = rule.createTmpDir();
        SpecialEnvSlave slave = new SpecialEnvSlave(remoteFS, rule.createComputerLauncher(/* yes null */null), nodeName, labels != null ? labels : "", env);
        rule.jenkins.addNode(slave);
        return slave;
    }
    private static class SpecialEnvSlave extends Slave {
        private final Map<String,String> env;
        SpecialEnvSlave(File remoteFS, ComputerLauncher launcher, String nodeName, @NonNull String labels, Map<String,String> env) throws Descriptor.FormException, IOException {
            super(nodeName, nodeName, remoteFS.getAbsolutePath(), 1, Node.Mode.NORMAL, labels, launcher, RetentionStrategy.NOOP, Collections.emptyList());
            this.env = env;
        }
        @Override public Computer createComputer() {
            return new SpecialEnvComputer(this, env);
        }
    }
    private static class SpecialEnvComputer extends SlaveComputer {
        private final Map<String,String> env;
        SpecialEnvComputer(SpecialEnvSlave slave, Map<String,String> env) {
            super(slave);
            this.env = env;
        }
        @Override public EnvVars getEnvironment() throws IOException, InterruptedException {
            EnvVars env2 = super.getEnvironment();
            env2.overrideAll(env);
            return env2;
        }
    }

    public static final class WrapperWithWorkspaceRequirement extends SimpleBuildWrapper {
        @DataBoundConstructor public WrapperWithWorkspaceRequirement() { }
        @Override public void setUp(@NonNull Context context, @NonNull Run<?, ?> build, @NonNull FilePath workspace, @NonNull Launcher launcher, @NonNull TaskListener listener, @NonNull EnvVars initialEnvironment) throws IOException, InterruptedException {
            listener.getLogger().println(">>> workspace context required and provided.");
            context.setDisposer(new DisposerWithWorkspaceRequirement());
        }
        @Override public void setUp(@NonNull Context context, @NonNull Run<?, ?> build, @NonNull TaskListener listener, @NonNull EnvVars initialEnvironment) throws IOException, InterruptedException {
            listener.getLogger().println(">>> workspace context required but not provided!");
            context.setDisposer(new DisposerWithWorkspaceRequirement());
        }
        public static final class DisposerWithWorkspaceRequirement extends Disposer {
            @Override public void tearDown(@NonNull Run<?, ?> build, @NonNull FilePath workspace, @NonNull Launcher launcher, @NonNull TaskListener listener) throws IOException, InterruptedException {
                listener.getLogger().println("<<< workspace context required and provided.");
            }
            @Override public void tearDown(@NonNull Run<?, ?> build, @NonNull TaskListener listener) throws IOException, InterruptedException {
                listener.getLogger().println("<<< workspace context required but not provided!");
            }
        }
        @Symbol("wrapperWithWorkspaceRequirement")
        @TestExtension("wrapperWithWorkspaceRequirement")
        public static class DescriptorImpl extends BuildWrapperDescriptor {
            @Override public boolean isApplicable(AbstractProject<?, ?> project) { return true; }
        }
    }

    @Issue("JENKINS-46175")
    @Test
    public void wrapperWithWorkspaceRequirement() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                // make sure it works inside a node
                p.setDefinition(new CpsFlowDefinition("node { wrapperWithWorkspaceRequirement { echo 'wrapped' } }", true));
                {
                    Run<?, ?> r = j.buildAndAssertSuccess(p);
                    j.assertLogContains(">>> workspace context required and provided.", r);
                    j.assertLogContains("<<< workspace context required and provided.", r);
                }
                // but fails outside of one
                p.setDefinition(new CpsFlowDefinition("wrapperWithWorkspaceRequirement { echo 'wrapped' }", true));
                {
                    Run<?, ?> r = j.buildAndAssertStatus(Result.FAILURE, p);
                    j.assertLogContains(MissingContextVariableException.class.getCanonicalName(), r);
                }
        });
    }

    public static final class WrapperWithoutWorkspaceRequirement extends SimpleBuildWrapper {
        @DataBoundConstructor public WrapperWithoutWorkspaceRequirement() { }
        @Override public boolean requiresWorkspace() { return false; }
        @Override public void setUp(@NonNull Context context, @NonNull Run<?, ?> build, @NonNull FilePath workspace, @NonNull Launcher launcher, @NonNull TaskListener listener, @NonNull EnvVars initialEnvironment) throws IOException, InterruptedException {
            listener.getLogger().println(">>> workspace context not needed, but provided.");
            context.setDisposer(new DisposerWithoutWorkspaceRequirement());
        }
        @Override public void setUp(@NonNull Context context, @NonNull Run<?, ?> build, @NonNull TaskListener listener, @NonNull EnvVars initialEnvironment) throws IOException, InterruptedException {
            listener.getLogger().println(">>> workspace context not needed.");
            context.setDisposer(new DisposerWithoutWorkspaceRequirement());
        }
        public static final class DisposerWithoutWorkspaceRequirement extends Disposer {
            @Override public void tearDown(@NonNull Run<?, ?> build, @NonNull FilePath workspace, @NonNull Launcher launcher, @NonNull TaskListener listener) throws IOException, InterruptedException {
                listener.getLogger().println("<<< workspace context not needed, but provided.");
            }
            @Override public void tearDown(@NonNull Run<?, ?> build, @NonNull TaskListener listener) throws IOException, InterruptedException {
                listener.getLogger().println("<<< workspace context not needed.");
            }
        }
        @Symbol("wrapperWithoutWorkspaceRequirement")
        @TestExtension("wrapperWithoutWorkspaceRequirement")
        public static class DescriptorImpl extends BuildWrapperDescriptor {
            @Override public boolean isApplicable(AbstractProject<?, ?> project) { return true; }
        }
    }

    @Issue("JENKINS-46175")
    @Test
    public void wrapperWithoutWorkspaceRequirement() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                // make sure it works outside of a node
                p.setDefinition(new CpsFlowDefinition("wrapperWithoutWorkspaceRequirement { echo 'wrapped' }", true));
                {
                    Run<?, ?> r = j.buildAndAssertSuccess(p);
                    j.assertLogContains(">>> workspace context not needed.", r);
                    j.assertLogContains("<<< workspace context not needed.", r);
                }
                // but also inside of one
                p.setDefinition(new CpsFlowDefinition("node { wrapperWithoutWorkspaceRequirement { echo 'wrapped' } }", true));
                {
                    Run<?, ?> r = j.buildAndAssertSuccess(p);
                    j.assertLogContains(">>> workspace context not needed, but provided.", r);
                    j.assertLogContains("<<< workspace context not needed, but provided.", r);
                }
        });
    }

}
