/*
 * The MIT License
 *
 * Copyright 2015 CLoudbees, Inc.
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

import static org.junit.jupiter.api.Assertions.*;

import hudson.FilePath;
import java.io.File;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class DeleteDirStepTest {

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void testDeleteEmptyWorkspace() throws Exception {
        String workspace = runAndGetWorkspaceDir(
                """
                        node {
                          deleteDir()
                        }""");
        File f = new File(workspace);
        assertFalse(f.exists(), "Workspace directory should no longer exist");
    }

    @Test
    void testDeleteTopLevelDir() throws Exception {
        String workspace = runAndGetWorkspaceDir(
                """
                        node {
                          writeFile file: 'f1', text: 'some text'
                          writeFile file: 'f2', text: 'some text'
                          writeFile file: '.hidden', text: 'some text'
                          writeFile file: 'sub1/f1', text: 'some text'
                          writeFile file: '.sub2/f2', text: 'some text'
                          writeFile file: '.sub3/.hidden', text: 'some text'
                          echo 'workspace is ---' + pwd() + '---'
                          deleteDir()
                        }""");
        File f = new File(workspace);
        assertFalse(f.exists(), "Workspace directory should no longer exist");
    }

    @Test
    void testDeleteSubFolder() throws Exception {
        String workspace = runAndGetWorkspaceDir(
                """
                        node {
                          writeFile file: 'f1', text: 'some text'
                          writeFile file: 'f2', text: 'some text'
                          writeFile file: '.hidden', text: 'some text'
                          writeFile file: 'sub1/f1', text: 'some text'
                          writeFile file: '.sub2/f2', text: 'some text'
                          writeFile file: '.sub3/.hidden', text: 'some text'
                          echo 'workspace is ---' + pwd() + '---'
                          dir ('sub1') {
                            deleteDir()
                          }\
                        }""");

        File f = new File(workspace);
        assertTrue(f.exists(), "Workspace directory should still exist");
        assertTrue(new File(f, "f1").exists(), "f1 should still exist");
        assertTrue(new File(f, "f2").exists(), "f1 should still exist");
        assertFalse(new File(f, "sub1").exists(), "sub1 should not exist");
        assertTrue(new File(f, ".sub2/f2").exists(), ".sub2/f2 should still exist");
        assertTrue(new File(f, ".sub3/.hidden").exists(), ".sub3/.hidden should still exist");
    }

    /**
     * Runs the given flow and returns the workspace used.
     * @param flow a flow definition.
     * @return the workspace used.
     */
    private String runAndGetWorkspaceDir(String flow) throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");

        p.setDefinition(new CpsFlowDefinition(flow, true));
        r.assertBuildStatusSuccess(p.scheduleBuild2(0));

        FilePath ws = r.jenkins.getWorkspaceFor(p);
        String workspace = ws.getRemote();
        assertNotNull(workspace, "Unable to locate workspace");
        return workspace;
    }
}
