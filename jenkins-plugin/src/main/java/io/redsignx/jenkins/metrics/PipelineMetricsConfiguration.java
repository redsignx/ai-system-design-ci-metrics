package io.redsignx.jenkins.metrics;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Global configuration for Pipeline Metrics Plugin.
 * Accessible via Manage Jenkins → Configure System.
 */
@Extension
public class PipelineMetricsConfiguration extends GlobalConfiguration {

    private String endpointUrl;
    private Secret bearerToken;
    private int connectionTimeoutSeconds = 10;
    private int readTimeoutSeconds = 30;
    private int maxQueueSize = 1000;
    private int maxRetries = 3;
    private int initialRetryDelaySeconds = 2;

    public PipelineMetricsConfiguration() {
        load();
    }

    public static PipelineMetricsConfiguration get() {
        return GlobalConfiguration.all().get(PipelineMetricsConfiguration.class);
    }

    @CheckForNull
    public String getEndpointUrl() {
        return endpointUrl;
    }

    @DataBoundSetter
    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
        save();
    }

    @CheckForNull
    public Secret getBearerToken() {
        return bearerToken;
    }

    @DataBoundSetter
    public void setBearerToken(Secret bearerToken) {
        this.bearerToken = bearerToken;
        save();
    }

    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    @DataBoundSetter
    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
        save();
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    @DataBoundSetter
    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
        save();
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    @DataBoundSetter
    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
        save();
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    @DataBoundSetter
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        save();
    }

    public int getInitialRetryDelaySeconds() {
        return initialRetryDelaySeconds;
    }

    @DataBoundSetter
    public void setInitialRetryDelaySeconds(int initialRetryDelaySeconds) {
        this.initialRetryDelaySeconds = initialRetryDelaySeconds;
        save();
    }

    public FormValidation doCheckEndpointUrl(@QueryParameter String value) {
        if (value == null || value.trim().isEmpty()) {
            return FormValidation.ok();
        }
        try {
            new URL(value);
            return FormValidation.ok();
        } catch (MalformedURLException e) {
            return FormValidation.error("Invalid URL: " + e.getMessage());
        }
    }

    public FormValidation doCheckMaxQueueSize(@QueryParameter int value) {
        if (value < 1) {
            return FormValidation.error("Queue size must be at least 1");
        }
        if (value > 10000) {
            return FormValidation.warning("Very large queue size may consume significant memory");
        }
        return FormValidation.ok();
    }

    public FormValidation doCheckMaxRetries(@QueryParameter int value) {
        if (value < 0) {
            return FormValidation.error("Max retries must be non-negative");
        }
        if (value > 10) {
            return FormValidation.warning("High retry count may delay event delivery significantly");
        }
        return FormValidation.ok();
    }
}
