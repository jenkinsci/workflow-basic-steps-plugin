package org.jenkinsci.plugins.workflow.steps;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Result;
import hudson.model.TaskListener;

public class DebugStepExecution extends StepExecution {

	@SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Only used when starting.")
	private transient final Boolean isDebugEnabled;

	DebugStepExecution(Boolean isDebugEnabled, StepContext context) {
		super(context);
		this.isDebugEnabled = isDebugEnabled;
	}

	@Override
	public boolean start() throws Exception {
		StepContext context = getContext();
		if (isDebugEnabled) {
			context.newBodyInvoker().withDisplayName("DEBUG ENABLED").withCallback(BodyExecutionCallback.wrap(context)).start();
			return false;
		}
		getContext().get(TaskListener.class).getLogger().println("(SKIPPED)");
		context.onSuccess(Result.SUCCESS);
		return true;
	}

	@Override
	public void onResume() {
	}

	private static final long serialVersionUID = 1L;
}
