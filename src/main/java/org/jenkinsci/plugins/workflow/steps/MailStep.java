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

import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import jenkins.plugins.mailer.tasks.MimeMessageBuilder;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;

/**
 * Simple email sender step.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class MailStep extends Step {

    private String charset;

    public final String subject;

    public final String body;

    @DataBoundSetter
    public String from;

    @DataBoundSetter
    public String to;

    @DataBoundSetter
    public String cc;

    @DataBoundSetter
    public String bcc;

    @DataBoundSetter
    public String replyTo;

    private String mimeType;

    @DataBoundConstructor
    public MailStep(@Nonnull String subject, @Nonnull String body) {
        this.subject = subject;
        this.body = body;
    }

    @DataBoundSetter
    public void setCharset(String charset) {
        this.charset = Util.fixEmpty(charset);
    }

    public String getCharset() {
        return charset;
    }

    @DataBoundSetter
    public void setMimeType(String mimeType) {
        this.mimeType = Util.fixEmpty(mimeType);
    }

    public String getMimeType() {
        return mimeType;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new MailStepExecution(this, context);
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override public String getFunctionName() {
            return "mail";
        }

        @Override public String getDisplayName() {
            return "Mail";
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }

        @Override public String argumentsToString(Map<String, Object> namedArgs) {
            Object subject = namedArgs.get("subject");
            return subject instanceof String ? (String) subject : null;
        }
    }

    /**
     * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
     */
    public static class MailStepExecution extends SynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L;

        private transient final MailStep step;

        MailStepExecution(MailStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            MimeMessage mimeMessage = buildMimeMessage();
            Transport.send(mimeMessage);
            return null;
        }

        private MimeMessage buildMimeMessage() throws Exception {
            if (StringUtils.isBlank(step.subject) || StringUtils.isBlank(step.body)) {
                throw new AbortException("Email not sent. All mandatory properties must be supplied ('subject', 'body').");
            }

            MimeMessageBuilder messageBuilder = new MimeMessageBuilder().setListener(getContext().get(TaskListener.class));

            if (step.subject != null) {
                messageBuilder.setSubject(step.subject);
            }
            if (step.body != null) {
                messageBuilder.setBody(step.body);
            }
            if (step.from != null) {
                messageBuilder.setFrom(step.from);
            }
            if (step.replyTo != null) {
                messageBuilder.setReplyTo(step.replyTo);
            }
            if (step.to != null) {
                messageBuilder.addRecipients(step.to, Message.RecipientType.TO);
            }
            if (step.cc != null) {
                messageBuilder.addRecipients(step.cc, Message.RecipientType.CC);
            }
            if (step.bcc != null) {
                messageBuilder.addRecipients(step.bcc, Message.RecipientType.BCC);
            }
            if (step.charset != null) {
                messageBuilder.setCharset(step.charset);
            }
            if (step.mimeType != null) {
                messageBuilder.setMimeType(step.mimeType);
            }

            MimeMessage message = messageBuilder.buildMimeMessage();

            Address[] allRecipients = message.getAllRecipients();
            if (allRecipients == null || allRecipients.length == 0) {
                throw new AbortException("Email not sent. No recipients of any kind specified ('to', 'cc', 'bcc').");
            }

            return message;
        }
    }
}
