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

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Extension;
import hudson.PluginWrapper;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Simple step to fail fast if some plugin is not installed.
 */
public class RequirePluginsStep extends Step {

    @VisibleForTesting
    static PluginChecker PLUGIN_CHECKER = new DefaultPluginChecker();
    private final List<String> plugins;

    @DataBoundConstructor
    public RequirePluginsStep(List<String> plugins) {
        this.plugins = plugins;
    }

    public List<String> getPlugins() {
        return plugins;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(plugins, context);
    }

    /**
     * Separate behaviour to make it testable
     */
    interface PluginChecker {
        PluginWrapper getPlugin(String pluginId);

        boolean isNotInstalled(String pluginId);

        boolean isPluginVersionTooLow(String pluginId, String version);

        boolean isInstalledButInactive(String pluginId);
    }

    static class DefaultPluginChecker implements PluginChecker {
        @Override
        public PluginWrapper getPlugin(String pluginId) {
            return Jenkins.getActiveInstance().getPluginManager().getPlugin(pluginId);
        }

        @Override
        public boolean isNotInstalled(String pluginId) {
            return Jenkins.getActiveInstance().getPluginManager().getPlugin(pluginId) == null;
        }

        @Override
        public boolean isPluginVersionTooLow(String pluginId, String version) {
            final PluginWrapper plugin = Jenkins.getActiveInstance().getPluginManager().getPlugin(pluginId);
            if (plugin == null) {
                throw new IllegalStateException("call isNotInstalled() before");
            }
            return plugin.getVersion().compareTo(version) < 0;
        }

        @Override
        public boolean isInstalledButInactive(String pluginId) {
            final PluginWrapper plugin = Jenkins.getActiveInstance().getPluginManager().getPlugin(pluginId);
            if (plugin == null) {
                throw new IllegalStateException("call isNotInstalled() before");
            }
            return plugin.isActive() == false;
        }
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

        @Override
        public Step newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            String pluginsString = formData.getString("plugins");
            List<String> plugins = new ArrayList<>();
            for (String line : pluginsString.split("\r?\n")) {
                line = line.trim();
                if (!line.isEmpty()) {
                    plugins.add(line);
                }
            }
            return new RequirePluginsStep(plugins);
        }
    }

    public static class Execution extends SynchronousStepExecution<Void> {

        private static final long serialVersionUID = 1L;

        @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Only used when starting.")
        private transient final List<String> plugins;

        Execution(List<String> plugins, StepContext context) {
            super(context);
            this.plugins = plugins;
        }

        @Override
        protected Void run() throws Exception {

            List<String> notInstalled = new ArrayList<>();
            List<String> insufficientVersionIds = new ArrayList<>();
            List<String> installedButNotActive = new ArrayList<>();

            for (String pluginSpec : plugins) {
                pluginSpec = pluginSpec.trim();
                if (StringUtils.isBlank(pluginSpec)) {
                    continue;
                }
                String pluginId = pluginSpec;
                String version = null;
                if (pluginSpec.contains("@")) {
                    String[] versioned = pluginId.split("@");
                    pluginId = versioned[0];
                    version = versioned[1];
                }

                if (PLUGIN_CHECKER.isNotInstalled(pluginId)) {
                    notInstalled.add(pluginSpec);
                } else if (PLUGIN_CHECKER.isInstalledButInactive(pluginId)) {
                    installedButNotActive.add(pluginId);
                } else if (version != null && PLUGIN_CHECKER.isPluginVersionTooLow(pluginId, version)) {
                    insufficientVersionIds.add(pluginSpec);
                }
            }

            if (!(notInstalled.isEmpty() && insufficientVersionIds.isEmpty() && installedButNotActive.isEmpty())) {
                throw new AbortException("Some requirement was not fulfilled: " +
                                                 "Not installed:" + notInstalled +
                                                 ", Installed, but inactive: " + installedButNotActive +
                                                 ", Too old: " + insufficientVersionIds);
            }
            return null;
        }
    }
}
