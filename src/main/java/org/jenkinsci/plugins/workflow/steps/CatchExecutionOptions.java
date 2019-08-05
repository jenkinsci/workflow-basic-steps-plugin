/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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

import hudson.model.Result;
import java.io.Serializable;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.actions.WarningAction;

public interface CatchExecutionOptions extends Serializable {
    /**
     * A message to be printed when an error is caught.
     *
     * If {@link #getStepResultOnError} (by adding a {@link WarningAction})
     */
    @CheckForNull String getMessage();

    /**
     * The result that will be used for setting the build result if an error is caught.
     *
     * Return {@link Result#SUCCESS} to leave the build result unchanged. If {@link #isCatchInterruptions} 
     * returns {@code true}, then if a {@link FlowInterruptedException} is caught, its result will be used
     * instead of this value.
     */
    @Nonnull Result getBuildResultOnError();

    /**
     * The result that will be used for annotating the with {@link WarningAction}) if an error is caught.
     *
     * Return {@link Result#SUCCESS} to leave the step result unchanged. If {@link #isCatchInterruptions}
     * returns {@code true}, then if a {@link FlowInterruptedException} is caught, its result will be used
     * instead of this value.
     */
    @Nonnull Result getStepResultOnError();

    /**
     * Whether {@link FlowInterruptedException} should be caught and handled by the step or rethrown.
     *
     * {@link FlowInterruptedException} is commonly used to control the flow of execution for things
     * like builds aborted by a user and builds that time out inside of {@link TimeoutStep}. It is
     * sometimes desirable to rethrow these kinds of exceptions rather than catching them so as to
     * not interfere with their intended behavior.
     */
    boolean isCatchInterruptions();
}
