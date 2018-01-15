/*
 * The MIT License
 *
 * Copyright (c) 2018, Joseph Petersen
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
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.TaskListener;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * Obtains a list of node names by their label
 */
public class NodesByLabelStep extends Step {

    private final String label;

    @DataBoundConstructor
    public NodesByLabelStep(String label) {
        this.label = label;
    }
    
    public String getLabel() {
        return label;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(label, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "nodesByLabel";
        }

        @Override
        public String getDisplayName() {
            return "List of node names by their label";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }
    }

    public static class Execution extends SynchronousStepExecution<ArrayList<String>> {
        
        @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used when starting.")
        private transient final String label;

        Execution(String label, StepContext context) {
            super(context);
            this.label = label;
        }

        @Override protected ArrayList<String> run() throws Exception {
            Label aLabel = Label.get(this.label);
            Set<Node> nodeSet = aLabel.getNodes();
            PrintStream logger = getContext().get(TaskListener.class).getLogger();
            ArrayList<String> nodes = new ArrayList<>();
            for (Node node : nodeSet) {
                Computer computer = node.toComputer();
                if (!(computer == null || computer.isOffline())) nodes.add(node.getNodeName());
            }
            if (nodes.isEmpty()) {
                logger.println("Could not find any nodes with '" + label + "' label");
            } else {
                logger.println("Found a total of " + nodes.size() + " nodes with the '" + label + "' label");
            }
            return nodes;
        }

        private static final long serialVersionUID = 1L;

    }

}
