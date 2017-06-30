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

import com.google.common.collect.ImmutableSet;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.util.ListBoxModel;
import java.util.Map;

import javax.annotation.CheckForNull;

import org.jenkinsci.plugins.structs.SymbolLookup;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.util.Set;

/**
 * Binds a {@link ToolInstallation} to a variable.
 */
public final class ToolStep extends Step {

    /** Same as {@link ToolInstallation#getName}. */
    private final String name;
    /** {@link ToolDescriptor#getId}, optional for disambiguation. */
    private @CheckForNull String type;

    @DataBoundConstructor public ToolStep(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    @DataBoundSetter public void setType(String type) {
        this.type = Util.fixEmpty(type);
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    @Extension public static final class DescriptorImpl extends StepDescriptor {

        @Override public String getFunctionName() {
            return "tool";
        }

        @Override public String getDisplayName() {
            return "Use a tool from a predefined Tool Installation";
        }

        public ListBoxModel doFillTypeItems() {
            ListBoxModel r = new ListBoxModel();
            r.add("<any>", "");
            for (ToolDescriptor<?> desc : ToolInstallation.all()) {
                String idOrSymbol = desc.getId();

                Set<String> symbols = SymbolLookup.getSymbolValue(desc);

                if (!symbols.isEmpty()) {
                    idOrSymbol = symbols.iterator().next();
                }

                r.add(desc.getDisplayName(), idOrSymbol);
            }
            return r;
        }

        public ListBoxModel doFillNameItems(@QueryParameter String type) {
            type = Util.fixEmpty(type);
            ListBoxModel r = new ListBoxModel();

            for (ToolDescriptor<?> desc : ToolInstallation.all()) {
                if (type != null && !desc.getId().equals(type) && !SymbolLookup.getSymbolValue(desc).contains(type)) {
                    continue;
                }
                for (ToolInstallation tool : desc.getInstallations()) {
                    r.add(tool.getName());
                }
            }
            return r;
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(TaskListener.class, EnvVars.class, Node.class);
        }

        @Override public String argumentsToString(Map<String, Object> namedArgs) {
            Object name = namedArgs.get("name");
            return name instanceof String ? (String) name : null;
        }

    }

    public static final class Execution extends SynchronousNonBlockingStepExecution<String> {

        private transient final ToolStep step;

        Execution(ToolStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override protected String run() throws Exception {
            String name = step.getName();
            String type = step.getType();
            for (ToolDescriptor<?> desc : ToolInstallation.all()) {
                if (type != null && !desc.getId().equals(type) && !SymbolLookup.getSymbolValue(desc).contains(type)) {
                    continue;
                }
                for (ToolInstallation tool : desc.getInstallations()) {
                    if (tool.getName().equals(name)) {
                        if (tool instanceof NodeSpecific) {
                            tool = (ToolInstallation) ((NodeSpecific<?>) tool).forNode(getContext().get(Node.class), getContext().get(TaskListener.class));
                        }
                        if (tool instanceof EnvironmentSpecific) {
                            tool = (ToolInstallation) ((EnvironmentSpecific<?>) tool).forEnvironment(getContext().get(EnvVars.class));
                        }

                        return tool.getHome();
                    }
                }
            }
            throw new AbortException("No " + (type != null ? type : "tool") + " named " + name + " found");
        }

        private static final long serialVersionUID = 1L;

    }

}
