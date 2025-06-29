/*
 * The MIT License
 *
 * Copyright 2025 Stuart Rowe
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import java.io.IOException;
import java.util.AbstractCollection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.TagsAction;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.StepNode;
import org.jenkinsci.plugins.workflow.support.steps.StageStep;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A conditional block scoped step for a stage
 */
public class WhenStep extends Step {

    // from org.jenkinsci.plugins.pipeline.StageStatus
    private static final String TAG_STAGE_STATUS = "STAGE_STATUS";
    private static final String SKIPPED_STAGE_STATUS = "SKIPPED_FOR_CONDITIONAL";

    private boolean condition;

    @DataBoundConstructor
    public WhenStep(Object condition) {
        // Support for a reasonable subset of expressions following the rules of Groovy Truth (see
        // https://groovy-lang.org/semantics.html#the-groovy-truth)
        if (condition != null) {
            if (condition instanceof Boolean) {
                // Boolean expressions
                this.condition = ((Boolean) condition).booleanValue();
            } else if (condition instanceof AbstractCollection) {
                // Collections and Arrays
                this.condition = !((AbstractCollection<?>) condition).isEmpty();
            } else if (condition instanceof Map) {
                // Maps
                this.condition = !((Map) condition).isEmpty();
            } else if (condition instanceof String) {
                // Strings
                this.condition = StringUtils.isNotEmpty((String) condition);
            } else if (condition instanceof Number) {
                // Numbers
                this.condition = ((Number) condition).intValue() != 0;
            } else {
                // Object References
                this.condition = true;
            }
        } else {
            this.condition = false;
        }
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "when";
        }

        @Override
        public String getDisplayName() {
            return "Conditionally execute a block of code";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            Set<Class<?>> context = new HashSet<>();
            Collections.addAll(context, FlowNode.class, Run.class);
            return Collections.unmodifiableSet(context);
        }
    }

    public static class Execution extends StepExecution {

        private static final long serialVersionUID = 1L;

        private final transient WhenStep whenStep;

        Execution(WhenStep whenStep, StepContext context) {
            super(context);
            this.whenStep = whenStep;
        }

        @Override
        public boolean start() throws IOException, InterruptedException {
            StepContext context = getContext();
            if (!whenStep.condition) {
                markStageSkipped();
                context.onSuccess(null);
                return true;
            }
            context.newBodyInvoker()
                    .withCallback(BodyExecutionCallback.wrap(context))
                    .start();
            return false;
        }

        @Override
        public void stop(@NonNull Throwable cause) throws Exception {
            getContext().onFailure(cause);
        }

        @Override
        public void onResume() {
            // do nothing
        }

        private void markStageSkipped() throws IOException, InterruptedException {
            FlowNode stageNode = getStageNode(getContext().get(FlowNode.class));
            if (stageNode != null) {
                TagsAction tagsAction = stageNode.getAction(TagsAction.class);
                if (tagsAction == null) {
                    tagsAction = new TagsAction();
                    stageNode.addAction(tagsAction);
                }
                if (tagsAction.getTagValue(TAG_STAGE_STATUS) == null) {
                    tagsAction.addTag(TAG_STAGE_STATUS, SKIPPED_STAGE_STATUS);
                    stageNode.save();
                }
            }
        }

        private FlowNode getStageNode(FlowNode node) {
            for (BlockStartNode bsn : node.iterateEnclosingBlocks()) {
                if (isStageNode(bsn)) {
                    return bsn;
                }
            }
            return null;
        }

        private boolean isStageNode(FlowNode node) {
            if (node instanceof StepNode) {
                StepDescriptor descriptor = ((StepNode) node).getDescriptor();
                if (descriptor instanceof StageStep.DescriptorImpl) {
                    LabelAction labelAction = node.getAction(LabelAction.class);
                    if (labelAction != null) {
                        return true;
                    }
                }
            }

            return false;
        }
    }
}
