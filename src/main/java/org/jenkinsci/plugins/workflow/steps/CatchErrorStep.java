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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Extension;
import hudson.Functions;
import hudson.Util;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.cps.steps.ParallelStep;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Runs a block.
 * By default, if that block fails, marks the build as failed, but continues execution. Can be customized to print a 
 * message when the block fails, to set a different build result, to annotate the step with {@link WarningAction} for
 * advanced visualizations, or to rethrow {@link FlowInterruptedException} rather than continuing execution.
 */
public final class CatchErrorStep extends Step implements CatchExecutionOptions {
    private static final long serialVersionUID = 1L;

    private @CheckForNull String message;
    private @Nonnull String buildResult = Result.FAILURE.toString();
    // This result is actually associated with the step, but this name makes more sense to users.
    private @Nonnull String stageResult = Result.SUCCESS.toString();
    private boolean catchInterruptions = true;

    @DataBoundConstructor public CatchErrorStep() {}

    @Override
    public String getMessage() {
        return message;
    }

    @DataBoundSetter
    public void setMessage(String message) {
        this.message = Util.fixEmptyAndTrim(message);
    }

    @Override
    public Result getBuildResultOnError() {
        return Result.fromString(buildResult);
    }

    public String getBuildResult() {
        return buildResult;
    }

    @DataBoundSetter
    public void setBuildResult(String buildResult) {
        if (buildResult == null) {
            buildResult = Result.SUCCESS.toString();
        }
        if (!buildResult.equalsIgnoreCase(Result.fromString(buildResult).toString())) {
            throw new IllegalArgumentException("buildResult is invalid: " + buildResult + ". Valid options are SUCCESS, UNSTABLE, FAILURE, NOT_BUILT and ABORTED.");
        }
        this.buildResult = buildResult;
    }

    @Override
    public Result getStepResultOnError() {
        return Result.fromString(stageResult);
    }

    public String getStageResult() {
        return stageResult;
    }

    @DataBoundSetter
    public void setStageResult(String stageResult) {
        if (stageResult == null) {
            stageResult = Result.SUCCESS.toString();
        }
        if (!stageResult.equalsIgnoreCase(Result.fromString(stageResult).toString())) {
            throw new IllegalArgumentException("stageResult is invalid: " + stageResult + ". Valid options are SUCCESS, UNSTABLE, FAILURE, NOT_BUILT and ABORTED.");
        }
        this.stageResult = stageResult;
    }

    @Override
    public boolean isCatchInterruptions() {
        return catchInterruptions;
    }

    @DataBoundSetter
    public void setCatchInterruptions(boolean catchInterruptions) {
        this.catchInterruptions = catchInterruptions;
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        ObjectInputStream.GetField fields = ois.readFields();
        message = (String) fields.get("message", null);
        catchInterruptions = fields.get("catchInterruptions", true);
        // Previously, the types of buildResult and stageResult were Result rather than String, so we handle either type.
        Object serializedBuildResult = fields.get("buildResult", "FAILURE");
        if (serializedBuildResult instanceof Result) {
            buildResult = ((Result) serializedBuildResult).toString();
        } else {
            buildResult = (String) serializedBuildResult;
        }
        Object serializedStageResult = fields.get("stageResult", "SUCCESS");
        if (serializedStageResult instanceof Result) {
            stageResult = ((Result) serializedStageResult).toString();
        } else {
            stageResult = (String) serializedStageResult;
        }
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new Execution(context, this);
    }

    @Extension public static final class DescriptorImpl extends StepDescriptor {

        @Override public String getFunctionName() {
            return "catchError";
        }

        @Override public String getDisplayName() {
            return "Catch error and set build result to failure";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(FlowNode.class, Run.class, TaskListener.class);
        }

        public ListBoxModel doFillBuildResultItems() {
            ListBoxModel r = new ListBoxModel();
            for (Result result : Arrays.asList(Result.SUCCESS, Result.UNSTABLE, Result.FAILURE, Result.NOT_BUILT, Result.ABORTED)) {
                r.add(result.toString());
            }
            return r;
        }

        public ListBoxModel doFillStageResultItems() {
            return doFillBuildResultItems();
        }

    }

    public static final class Execution extends AbstractStepExecutionImpl {
        @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used at startup, serialized in Callback")
        private transient final CatchExecutionOptions options;

        Execution(StepContext context, CatchExecutionOptions options) {
            super(context);
            this.options = options;
        }

        @Override public boolean start() throws Exception {
            StepContext context = getContext();
            context.newBodyInvoker()
                    .withCallback(new Callback(options))
                    .start();
            return false;
        }

        @Override public void onResume() {}

        private static final class Callback extends BodyExecutionCallback {
            private static final long serialVersionUID = -5448044884830236797L;
            private CatchExecutionOptions options;

            public Callback(CatchExecutionOptions options) {
                this.options = options;
            }

            public Object readResolve() {
                if (options == null) {
                    options = DEFAULT_OPTIONS;
                }
                return this;
            }

            @Override public void onSuccess(StepContext context, Object result) {
                context.onSuccess(null); // we do not pass up a result, since onFailure cannot
            }

            @Override public void onFailure(StepContext context, Throwable t) {
                try {
                    if (!options.isCatchInterruptions() && t instanceof FlowInterruptedException && ((FlowInterruptedException)t).isActualInterruption()) {
                        context.onFailure(t);
                        return;
                    }
                    TaskListener listener = context.get(TaskListener.class);
                    String message = options.getMessage();
                    if (message != null) {
                        listener.error(message);
                    }
                    Result buildResult = options.getBuildResultOnError();
                    Result stepResult = options.getStepResultOnError();
                    if (t instanceof AbortException) {
                        listener.error(t.getMessage());
                    } else if (t instanceof FlowInterruptedException && !isCausedByFailFastException((FlowInterruptedException) t)) {
                        FlowInterruptedException fie = (FlowInterruptedException) t;
                        fie.handle(context.get(Run.class), listener);
                        buildResult = fie.getResult();
                        stepResult = fie.getResult();
                    } else {
                        Functions.printStackTrace(t, listener.getLogger());
                    }
                    if (buildResult.isWorseThan(Result.SUCCESS)) {
                        context.get(Run.class).setResult(buildResult);
                    }
                    if (stepResult.isWorseThan(Result.SUCCESS)) {
                        context.get(FlowNode.class).addOrReplaceAction(new WarningAction(stepResult).withMessage(message));
                    }
                    context.onSuccess(null);
                } catch (Exception x) {
                    context.onFailure(x);
                }
            }

            private boolean isCausedByFailFastException(FlowInterruptedException fie) {
                return fie.getCauses().stream()
                        .filter(coi -> coi instanceof ExceptionCause)
                        .map(coi -> (ExceptionCause) coi)
                        .map(ExceptionCause::getException)
                        .anyMatch(ec -> ec instanceof ParallelStep.FailFastException);
            }
        }

        private static final long serialVersionUID = 1L;

    }

    private static final CatchExecutionOptions DEFAULT_OPTIONS = new CatchExecutionOptions() {
        private static final long serialVersionUID = 1L;

        @Override
        public String getMessage() {
            return null;
        }

        @Override
        public Result getBuildResultOnError() {
            return Result.FAILURE;
        }

        @Override
        public Result getStepResultOnError() {
            return Result.SUCCESS;
        }

        @Override
        public boolean isCatchInterruptions() {
            return true;
        }
    };
}
