package org.jenkinsci.plugins.workflow.steps;

import hudson.ProxyConfiguration;
import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Robin Müller
 */
public class HttpProxyEnvStepTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void test_proxy_not_set() throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "workflow");
        job.setDefinition(new CpsFlowDefinition("withHttpProxyEnv { " +
                                                "  echo \"proxy: $env.http_proxy\" " +
                                                "}", true));

        WorkflowRun run = jenkinsRule.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get());
        jenkinsRule.assertLogContains("proxy: null", run);
    }

    @Test
    public void test_proxy_set() throws Exception {
        jenkinsRule.getInstance().proxy = new ProxyConfiguration("test", 8080);

        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "workflow");
        job.setDefinition(new CpsFlowDefinition("withHttpProxyEnv { " +
                                                "  echo \"proxy: $env.http_proxy\" " +
                                                "}", true));
        WorkflowRun run = jenkinsRule.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get());
        jenkinsRule.assertLogContains("proxy: http://test:8080", run);
    }

    @Test
    public void test_noProxyHosts_not_set() throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "workflow");
        job.setDefinition(new CpsFlowDefinition("withHttpProxyEnv { " +
                                                "  echo \"noProxyHosts: $env.no_proxy\" " +
                                                "}", true));

        WorkflowRun run = jenkinsRule.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get());
        jenkinsRule.assertLogContains("noProxyHosts: null", run);
    }

    @Test
    public void test_noProxyHosts_set() throws Exception {
        jenkinsRule.getInstance().proxy = new ProxyConfiguration("test", 8080, null, null, "test.tld\n*.test.tld");

        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "workflow");
        job.setDefinition(new CpsFlowDefinition("withHttpProxyEnv { " +
                                                "  echo \"noProxyHosts: $env.no_proxy\" " +
                                                "}", true));

        WorkflowRun run = jenkinsRule.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get());
        jenkinsRule.assertLogContains("noProxyHosts: test.tld,*.test.tld", run);
    }
}
