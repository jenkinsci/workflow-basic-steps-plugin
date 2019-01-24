/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import com.ctc.wstx.util.StringUtil;

/**
 * A simple echo back statement.
 */
public class EchoStep extends Step {

    private static final int MAX_LABEL_LENGTH = 100;

    private String message;
    private String label;

    @DataBoundConstructor
    public EchoStep(String message) {
        this.message = message;
    }

    @DataBoundSetter
    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    @DataBoundSetter
    public void setLabel(@Nonnull String label) {
        this.label = label.trim();
    }

     public String getLabel() {
        return label;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        String label = StringUtils.trimToNull(this.label);

        // If the label isn't empty when we get here
        if (label != null) {
            context.get(FlowNode.class).addAction(new LabelAction(StringUtils.abbreviate(label, MAX_LABEL_LENGTH)));
        }

        return new Execution(message, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "echo";
        }

        @Override
        public String getDisplayName() {
            return "Print Message";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }

        @Override public String argumentsToString(Map<String, Object> namedArgs) {
            Object message = namedArgs.get("message");
            String messageString = message instanceof String ? (String) message : null;
            // When reporting echo as string limit it to the first line
            if (StringUtils.contains(messageString, "\n")) {
                messageString = StringUtils.trimToNull(StringUtils.substringBefore(messageString, "\n"));
            }
            return messageString;
        }
    }

    public static class Execution extends SynchronousStepExecution<Void> {

        @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used when starting.")
        private transient final String message;

        Execution(String message, StepContext context) {
            super(context);
            this.message = message;
        }

        @Override protected Void run() throws Exception {
            getContext().get(TaskListener.class).getLogger().println(message);
            return null;
        }

        private static final long serialVersionUID = 1L;

    }

}
