/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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
import hudson.EnvVars;
import hudson.Extension;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All the variables not provided in the parameter will be filtered out for the enclosed block
 */
public class RetainEnvStep extends Step {

    /**
     * Environment variable to keep.
     */
    private final List<String> variables;

    @DataBoundConstructor
    public RetainEnvStep(List<String> variables) {
        this.variables = new ArrayList<>(variables);
    }

    public List<String> getVariables() {
        return variables;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(variables, context);
    }

    public static class Execution extends AbstractStepExecutionImpl {

        private static final long serialVersionUID = 1;

        @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used when starting.")
        private transient final List<String> variables;

        Execution(List<String> variables, StepContext context) {
            super(context);
            this.variables = variables;
        }

        @Override
        public boolean start() throws Exception {
            getContext().newBodyInvoker()
                    .withContext(EnvironmentExpander.merge(
                            getContext().get(EnvironmentExpander.class),
                            new FilteredEnvironmentExpander(variables)
                    ))
                    .withCallback(BodyExecutionCallback.wrap(getContext()))
                    .start();
            return false;
        }

        @Override
        public void onResume() {}
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "retainEnv";
        }

        @Override
        public String getDisplayName() {
            return "Keep only specified environment variables";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        // TODO JENKINS-27901: need a standard control for this
        @Override
        public Step newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            String variablesToKeepS = formData.getString("variables");
            List<String> variablesToKeep = new ArrayList<>();
            for (String line : variablesToKeepS.split("\r?\n")) {
                line = line.trim();
                if (!line.isEmpty()) {
                    variablesToKeep.add(line);
                }
            }
            return new RetainEnvStep(variablesToKeep);
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.emptySet();
        }

        @Override
        public String argumentsToString(Map<String, Object> namedArgs) {
            Object variablesToKeep = namedArgs.get("variables");
            if (variablesToKeep instanceof List) {
                StringBuilder b = new StringBuilder();
                for (Object variableName : (List) variablesToKeep) {
                    if (variableName instanceof String) {
                        if (b.length() > 0) {
                            b.append(", ");
                        }
                        b.append((String) variableName);
                    }
                }
                return b.toString();
            } else {
                return null;
            }
        }
    }

    public static class FilteredEnvironmentExpander extends EnvironmentExpander {
        private static final long serialVersionUID = 1;

        private final List<String> variables;

        public FilteredEnvironmentExpander(Collection<String> variables) {
            this.variables = /* ensure serializability*/ new ArrayList<>(variables);
        }

        @Override
        public void expand(EnvVars env) {
            List<String> keyList = new ArrayList<>(env.keySet());
            keyList.removeAll(variables);
            for (String key : keyList) {
                env.remove(key);
            }
        }
    }
}
