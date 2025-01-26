package org.jenkinsci.plugins.workflow.steps;

import com.google.common.base.Joiner;
import hudson.EnvVars;
import hudson.Extension;
import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Robin Müller
 */
public class HttpProxyEnvStep extends Step {

    @DataBoundConstructor
    public HttpProxyEnvStep() {
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "withHttpProxyEnv";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Set the global proxy settings as environment variables within a block";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.emptySet();
        }

    }

    public static class Execution extends AbstractStepExecutionImpl {

        private static final long serialVersionUID = 1;

        Execution(StepContext context) {
            super(context);
        }

        @Override
        public boolean start() throws Exception {
            StepContext context = getContext();
            context.newBodyInvoker()
                   .withContext(EnvironmentExpander.merge(context.get(EnvironmentExpander.class), new HttpProxyEnvironmentExpander()))
                   .withCallback(BodyExecutionCallback.wrap(context))
                   .start();
            return false;
        }

        @Override
        public void stop(Throwable cause) throws Exception {
            getContext().onFailure(cause);
        }

        @Override
        public void onResume() {
        }

    }

    private static class HttpProxyEnvironmentExpander extends EnvironmentExpander {
        @Override
        public void expand(@Nonnull EnvVars envVars) throws IOException, InterruptedException {
            ProxyConfiguration proxyConfiguration = Jenkins.getActiveInstance().proxy;
            if (proxyConfiguration != null) {
                Proxy proxy = proxyConfiguration.createProxy(null);
                InetSocketAddress address = (InetSocketAddress) proxy.address();
                String proxyHost = "http://" + address.getHostName() + ":" + address.getPort();
                envVars.put("http_proxy", proxyHost);
                envVars.put("https_proxy", proxyHost);
                envVars.put("HTTP_PROXY", proxyHost);
                envVars.put("HTTPS_PROXY", proxyHost);
                List<String> noProxyHosts = new ArrayList<>();
                for (Pattern noProxyHost : proxyConfiguration.getNoProxyHostPatterns()) {
                    noProxyHosts.add(noProxyHost.toString().replace("\\.", ".").replace(".*", "*"));
                }
                String noProxy = Joiner.on(',').join(noProxyHosts);
                envVars.put("no_proxy", noProxy);
                envVars.put("NO_PROXY", noProxy);
            }
        }
    }
}
