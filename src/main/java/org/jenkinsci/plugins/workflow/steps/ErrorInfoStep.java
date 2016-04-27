/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

import com.google.common.base.Predicate;
import com.google.inject.Inject;
import hudson.AbortException;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Result;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowScanner;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Step to supply contextual information about an error that has been caught.
 */
public class ErrorInfoStep extends AbstractStepImpl {

    public final Throwable error;

    @DataBoundConstructor public ErrorInfoStep(Throwable error) {
        this.error = error;
    }

    public static class Execution extends AbstractSynchronousStepExecution<ErrorInfo> {

        private static final long serialVersionUID = 1;
        @Inject private transient ErrorInfoStep step;
        @StepContextParameter private transient FlowExecution execution;

        @Override protected ErrorInfo run() throws Exception {
            return new ErrorInfo(step.error, execution);
        }

    }

    @Extension public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "errorInfo";
        }

        @Override public String getDisplayName() {
            return "Calculate information about an error";
        }

        // TODO blank config.jelly

    }

    public static class ErrorInfo implements Serializable {

        private static final long serialVersionUID = 1;
        private final Throwable error;
        private transient FlowExecution execution;
        private final FlowExecutionOwner executionOwner;

        ErrorInfo(Throwable error, FlowExecution execution) {
            this.error = error;
            this.execution = execution;
            executionOwner = execution.getOwner();
        }

        private FlowExecution getExecution() throws IOException {
            if (execution == null) {
                execution = executionOwner.get();
            }
            return execution;
        }

        /**
         * Finds a node which threw this exception or one of its causes.
         * Note that {@link Throwable#equals} is just pointer equality,
         * which we cannot use since we may be loading deserialized exceptions,
         * so we compare by stack trace instead.
         */
        private @CheckForNull FlowNode getNode() throws IOException {
            final Set<String> stackTraces = new HashSet<>();
            for (Throwable t = error; t != null; t = t.getCause()) {
                stackTraces.add(Functions.printThrowable(t));
            }
            Predicate<FlowNode> threwException = new Predicate<FlowNode>() {
                @Override
                public boolean apply(FlowNode input) {
                    if (input instanceof BlockEndNode) {
                        return false;
                    }
                    ErrorAction a = input.getAction(ErrorAction.class);
                    return (a != null && stackTraces.contains(Functions.printThrowable(a.getError())));
                }
            };
            return new FlowScanner.DepthFirstScanner().findFirstMatch(getExecution(), threwException);
        }

        @Whitelisted
        public @Nonnull Throwable getError() {
            return error;
        }

        /**
         * Gets the stack trace of the error, or just the message in the case of {@link AbortException}.
         */
        @Whitelisted
        public @Nonnull String getStackTrace() {
            if (error instanceof AbortException) {
                return error.getMessage();
            } else {
                return Functions.printThrowable(error);
            }
        }

        /**
         * Gets the {@link Result} of the build if the error were uncaught.
         * @return typically {@link Result#FAILURE} but {@link FlowInterruptedException} may override
         */
        @Whitelisted
        public @Nonnull String getResult() {
            Result r;
            if (error instanceof FlowInterruptedException) {
                r = ((FlowInterruptedException) error).getResult();
            } else {
                r = Result.FAILURE;
            }
            return r.toString();
        }

        /**
         * Looks for the URL of the {@link LogAction} last printed before the node which broke.
         */
        @Whitelisted
        public @CheckForNull String getLogURL() throws IOException {
            FlowNode n = getNode();
            if (n != null) {
                FlowNode logNode = new FlowScanner.LinearBlockHoppingScanner()
                        .findFirstMatch(n,
                                FlowScanner.MATCH_HAS_LOG);
                if (logNode != null) {
                    String u = Jenkins.getActiveInstance().getRootUrl();
                    if (u == null) {
                        u = "http://jenkins/"; // placeholder
                    }
                    return u + logNode.getUrl() + logNode.getAction(LogAction.class).getUrlName();
                }
            }
            return null;
        }

        // TODO tail of log
        // TODO label

    }

}
