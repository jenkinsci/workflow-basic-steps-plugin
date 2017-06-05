/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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
import hudson.tasks.Maven;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import java.io.File;
import java.util.List;
import java.util.Set;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.ToolInstallations;

public class ToolStepRunTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void build() throws Exception {
        Maven.MavenInstallation tool = ToolInstallations.configureMaven3();
        String name = tool.getName();
        Maven.MavenInstallation.DescriptorImpl desc = Jenkins.getInstance().getDescriptorByType(Maven.MavenInstallation.DescriptorImpl.class);

        // Defensive - Maven doesn't have a symbol before 2.x, and other tools may still not have symbols after that.
        String type = desc.getId();

        Set<String> symbols = SymbolLookup.getSymbolValue(desc);

        if (!symbols.isEmpty()) {
            type = symbols.iterator().next();
        }

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {def home = tool name: '" + name + "', type: '" + type + "'; def settings = readFile($/$home/conf/settings.xml/$).split(); echo settings[-1]}",
                true));

        r.assertLogContains("</settings>", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

    @Test public void toolWithSymbol() throws Exception {
        File toolHome = folder.newFolder("mockTools");
        MockToolWithSymbol tool = new MockToolWithSymbol("mock-tool-with-symbol", toolHome.getAbsolutePath(), JenkinsRule.NO_PROPERTIES);
        Jenkins.getInstance().getDescriptorByType(MockToolWithSymbol.MockToolWithSymbolDescriptor.class).setInstallations(tool);

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {def home = tool name: '" + tool.getName() + "', type: 'mockToolWithSymbol'\n"
                +"echo \"${home}\"}",
                true));

        r.assertLogContains(toolHome.getAbsolutePath(),
                r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

    @Test public void toolWithoutSymbol() throws Exception {
        File toolHome = folder.newFolder("mockTools");
        MockToolWithoutSymbol tool = new MockToolWithoutSymbol("mock-tool-without-symbol", toolHome.getAbsolutePath(), JenkinsRule.NO_PROPERTIES);
        Jenkins.getInstance().getDescriptorByType(MockToolWithoutSymbol.MockToolWithoutSymbolDescriptor.class).setInstallations(tool);

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node {def home = tool name: '" + tool.getName() + "', type: 'mockToolWithoutSymbol'}",
                true));

        r.assertLogContains("No mockToolWithoutSymbol named mock-tool-without-symbol found",
                r.assertBuildStatus(Result.FAILURE, r.waitForCompletion(p.scheduleBuild2(0).waitForStart())));

        p.setDefinition(new CpsFlowDefinition("node {def home = tool name: '" + tool.getName() + "', type: '" + MockToolWithoutSymbol.class.getName() + "'\n"
                + "echo \"${home}\"}",
                true));
        r.assertLogContains(toolHome.getAbsolutePath(),
                r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

    public static class MockToolWithSymbol extends ToolInstallation {
        public MockToolWithSymbol(String name, String home, List<? extends ToolProperty<?>> properties) {
            super(name, home, properties);
        }

        @TestExtension
        @Symbol("mockToolWithSymbol")
        public static class MockToolWithSymbolDescriptor extends ToolDescriptor<MockToolWithSymbol> {
        }
    }

    public static class MockToolWithoutSymbol extends ToolInstallation {
        public MockToolWithoutSymbol(String name, String home, List<? extends ToolProperty<?>> properties) {
            super(name, home, properties);
        }

        @TestExtension
        public static class MockToolWithoutSymbolDescriptor extends ToolDescriptor<MockToolWithoutSymbol> {
        }
    }
}
