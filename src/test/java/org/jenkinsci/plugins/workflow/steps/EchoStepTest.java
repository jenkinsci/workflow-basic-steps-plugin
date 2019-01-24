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

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable.Row;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class EchoStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void smokes() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("echo 'hello there'", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        List<LogAction> logActions = new ArrayList<LogAction>();
        for (FlowNode n : new FlowGraphWalker(b.getExecution())) {
            LogAction la = n.getAction(LogAction.class);
            if (la != null) {
                logActions.add(la);
            }
        }
        assertEquals(1, logActions.size());
        StringWriter w = new StringWriter();
        logActions.get(0).getLogText().writeLogTo(0, w);
        assertEquals("hello there", w.toString().trim());
        Matcher m = Pattern.compile("hello there").matcher(JenkinsRule.getLog(b));
        assertTrue("message printed once", m.find());
        assertFalse("message not printed twice", m.find());
    }

    @Test public void smokeMultiline() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("echo '''hello to \n you there \n '''", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        List<String> logActions = new ArrayList<String>();
        for (FlowNode n : new FlowGraphWalker(b.getExecution())) {
            ArgumentsAction la = n.getAction(ArgumentsAction.class);
            if (la != null) {
                logActions.add(ArgumentsAction.getStepArgumentsAsString(n));
            }
        }
        assertEquals(1, logActions.size());
        assertEquals("hello to", logActions.get(0));
        Matcher m = Pattern.compile("hello to").matcher(JenkinsRule.getLog(b));
        assertTrue("message printed once", m.find());
        assertFalse("message not printed twice", m.find());
    }

    @Test public void label() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("echo message: 'hello there', label: 'hello test label'", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        FlowGraphTable t = new FlowGraphTable(b.getExecution());
        t.build();
        int found = 0;
        for (Row r : t.getRows()) {
            if (r.getDisplayName().equals("hello test label")) {
                found += 1;
            }
        }
        assertEquals("Should have one row with the expected label", 1, found);
    }

    @Test public void labelEmpty() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("echo message: 'hello there', label: ''", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        List<LabelAction> actions = new ArrayList<LabelAction>();
        for (FlowNode n : new FlowGraphWalker(b.getExecution())) {
            LabelAction la = n.getAction(LabelAction.class);
            if (la != null) {
                actions.add(la);
            }
        }
        assertEquals(0, actions.size());
    }

    @Test public void labelWhitespace() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("echo message: 'hello there', label: '  \t  '", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        List<LabelAction> actions = new ArrayList<LabelAction>();
        for (FlowNode n : new FlowGraphWalker(b.getExecution())) {
            LabelAction la = n.getAction(LabelAction.class);
            if (la != null) {
                actions.add(la);
            }
        }
        assertEquals(0, actions.size());
    }

}
