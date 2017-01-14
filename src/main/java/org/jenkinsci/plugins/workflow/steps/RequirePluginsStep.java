/*
 * The MIT License
 *
 * Copyright (c) 2017 Baptiste Mathus.
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Extension;
import hudson.Plugin;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Simple step to fail fast if some plugin is not installed.
 */
public class RequirePluginsStep extends Step {

    private final String plugins;

    @DataBoundConstructor
    public RequirePluginsStep(String plugins) {
        this.plugins = plugins;
    }

    public String getPlugins() {
        return plugins;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(plugins, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "requirePlugins";
        }

        @Override
        public String getDisplayName() {
            return "Fail if one the plugins specified in the comma-separated list is not installed.";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }
    }

    public static class Execution extends SynchronousStepExecution<Void> {

        private static final long serialVersionUID = 1L;

        @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used when starting.")
        private transient final String plugins;

        Execution(String plugins, StepContext context) {
            super(context);
            this.plugins = plugins;
        }

        @Override
        protected Void run() throws Exception {

            String[] pluginIds = plugins.split("[, ]");
            List<String> absentIds = new ArrayList<>();
            List<String> insufficientVersionIds = new ArrayList<>();

            for (String pluginSpec : pluginIds) {
                if(StringUtils.isBlank(pluginSpec)) {
                    continue;
                }
                String pluginId = pluginSpec;
                String version = null;
                if (pluginSpec.contains("@")) {
                    String[] versioned = pluginId.split("@");
                    pluginId = versioned[0];
                    version = versioned[1];
                }

                final Plugin plugin = Jenkins.getActiveInstance().getPlugin(pluginId);
                if (plugin == null) {
                    absentIds.add(pluginSpec);
                }
                else if (version != null && plugin.getWrapper().getVersion().compareTo(version) < 0) {
                    insufficientVersionIds.add(pluginSpec);
                }
            }

            if (!(absentIds.isEmpty() && insufficientVersionIds.isEmpty())) {
                throw new AbortException("Some plugins are not installed or a too old version: " +
                                                 "Not installed:" + absentIds +
                                                 ", Too old: " + insufficientVersionIds);
            }
            return null;
        }
    }
}
