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
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jvnet.hudson.annotation_indexer.Index;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Simple step to fail fast if some step is not found.
 * <p>
 * <p><strong>WARNING: Global variables not supported yet</strong>.</p>
 */
public class RequireStepsStep extends Step {

    @VisibleForTesting
    static final String ERROR_LOG = "The following steps were not found (warning: global variables checking not supported): %s" +
            ". Neither among the functions: %s" +
            ", nor among the symbols: %s";

    private final List<String> steps;

    @DataBoundConstructor
    public RequireStepsStep(List<String> steps) {
        this.steps = steps;
    }

    public List<String> getSteps() {
        return steps;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(steps, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "requireSteps";
        }

        @Override
        public String getDisplayName() {
            return "Fail if one the specified steps in the list is not installed.";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }
    }

    public static class Execution extends SynchronousStepExecution<Void> {

        private static final long serialVersionUID = 1L;

        @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Only used when starting.")
        private transient final List<String> steps;

        Execution(List<String> steps, StepContext context) {
            super(context);
            this.steps = steps;
        }

        @Override
        protected Void run() throws Exception {

            // Essentially copied from workflow-cps-plugin's DSL.java https://git.io/vMwii
            Set<String> functions = getFunctions();
            Set<String> symbols = getSymbols();

            // FIXME : how to get GlobalVariables?
            // Set<String> globals = TODO

            List<String> notFound = new ArrayList<>();
            for (String step : steps) {
                if (!functions.contains(step) && !symbols.contains(step)) {
                    notFound.add(step);
                }
            }

            if (!notFound.isEmpty()) {
                throw new AbortException(String.format(
                        ERROR_LOG,
                        notFound, functions, symbols));
            }

            return null;
        }

        private Set<String> getSymbols() throws java.io.IOException {
            Set<String> symbols = new TreeSet<>();
            for (Class<?> e : Index.list(Symbol.class, Jenkins.getActiveInstance().pluginManager.uberClassLoader,
                                         Class.class)) {
                if (Descriptor.class.isAssignableFrom(e)) {
                    symbols.addAll(SymbolLookup.getSymbolValue(e));
                }
            }
            return symbols;
        }

        private TreeSet<String> getFunctions() {
            TreeSet<String> functions = new TreeSet<>();
            for (StepDescriptor d : StepDescriptor.all()) {
                functions.add(d.getFunctionName());
            }
            return functions;
        }
    }
}
