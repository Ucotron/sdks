package com.ucotron.spring;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Ucotron Spring Boot auto-configuration.
 * <p>
 * All properties are prefixed with {@code ucotron}.
 * <p>
 * Example {@code application.yml}:
 * <pre>
 * ucotron:
 *   server-url: http://localhost:8420
 *   api-key: my-secret-key
 *   namespace: my-app
 *   timeout: 30s
 *   retry:
 *     max-retries: 3
 *     base-delay: 100ms
 *     max-delay: 5s
 * </pre>
 */
@ConfigurationProperties(prefix = "ucotron")
public class UcotronProperties {

    /**
     * Ucotron server URL. Required.
     */
    private String serverUrl = "http://localhost:8420";

    /**
     * API key for authentication (optional).
     */
    private String apiKey;

    /**
     * Default namespace for multi-tenancy (optional).
     */
    private String namespace;

    /**
     * Connection and read timeout.
     */
    private Duration timeout = Duration.ofSeconds(30);

    /**
     * Whether to enable auto-configuration.
     */
    private boolean enabled = true;

    /**
     * Retry configuration.
     */
    private Retry retry = new Retry();

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    /**
     * Retry sub-configuration.
     */
    public static class Retry {
        private int maxRetries = 3;
        private Duration baseDelay = Duration.ofMillis(100);
        private Duration maxDelay = Duration.ofSeconds(5);

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public Duration getBaseDelay() {
            return baseDelay;
        }

        public void setBaseDelay(Duration baseDelay) {
            this.baseDelay = baseDelay;
        }

        public Duration getMaxDelay() {
            return maxDelay;
        }

        public void setMaxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
        }
    }
}
