/*
 * The MIT License
 *
 * Copyright 2017 Baptiste Mathus.
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
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Random;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RequirePluginsStepTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void allPluginsPresent() throws Exception {
        requirePlugins(new String[]{"workflow-step-api", "workflow-job"},
                       new String[]{},
                       new String[]{"workflow-step-api", "workflow-job"},
                       Result.SUCCESS);
    }

    @Test
    public void absentPlugin() throws Exception {
        requirePlugins(new String[]{"absent", "workflow-step-api", "nonthereatall"},
                       new String[]{"absent", "nonthereatall"},
                       new String[]{"workflow-step-api"},
                       Result.FAILURE);
    }

    @Test
    public void versionOK() throws Exception {
        requirePlugins(new String[]{"junit", " workflow-step-api@1.0 "},
                       new String[]{},
                       new String[]{"junit", "workflow-step-api@1.0"},
                       Result.SUCCESS);
    }

    @Test
    public void disabledPlugin() throws Exception {
        requirePlugins(new String[]{"junit"},
                       new String[]{},
                       new String[]{"junit"},
                       Result.SUCCESS);

        // Now mock the checker to force junit as inactive, and re-run the same test
        RequirePluginsStep.PLUGIN_CHECKER = new RequirePluginsStep.DefaultPluginChecker() {
            @Override
            public boolean isInstalledButInactive(String pluginId) {
                if ("junit".equals(pluginId)) {
                    return true;
                }
                return super.isInstalledButInactive(pluginId);
            }
        };

        requirePlugins(new String[]{"junit"},
                       new String[]{"junit"},
                       new String[]{},
                       Result.FAILURE);
    }

    @Test
    public void tooOld() throws Exception {
        requirePlugins(new String[]{"workflow-step-api@9.5"},
                       new String[]{"workflow-step-api@9.5"},
                       new String[]{},
                       Result.FAILURE);

    }


    private void requirePlugins(String[] requirement, String[] notInstalled, String[] installed, Result result) throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p" + new Random().nextInt());
        final String script = "requirePlugins plugins:" + asText(requirement);

        p.setDefinition(new CpsFlowDefinition(script, true));
        WorkflowRun b = r.assertBuildStatus(result, p.scheduleBuild2(0));

        String log = StringUtils.join(b.getLog(10), " ");
        for (String absent : notInstalled) {
            assertTrue(log.contains(absent));
        }

        for (String absent : installed) {
            assertFalse(log.contains(absent));
        }
    }

    private String asText(String[] requirement) {
        return "['" + StringUtils.join(requirement, "','") + "']";
    }
}
