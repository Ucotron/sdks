package com.ucotron.sdk;

import java.time.Duration;

/**
 * Configuration for the Ucotron client.
 */
public class ClientConfig {
    private final String apiKey;
    private final String defaultNamespace;
    private final Duration timeout;
    private final RetryConfig retryConfig;

    private ClientConfig(Builder builder) {
        this.apiKey = builder.apiKey;
        this.defaultNamespace = builder.defaultNamespace;
        this.timeout = builder.timeout;
        this.retryConfig = builder.retryConfig;
    }

    public String getApiKey() { return apiKey; }
    public String getDefaultNamespace() { return defaultNamespace; }
    public Duration getTimeout() { return timeout; }
    public RetryConfig getRetryConfig() { return retryConfig; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String apiKey;
        private String defaultNamespace;
        private Duration timeout = Duration.ofSeconds(30);
        private RetryConfig retryConfig = RetryConfig.defaults();

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder defaultNamespace(String namespace) {
            this.defaultNamespace = namespace;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder retryConfig(RetryConfig retryConfig) {
            this.retryConfig = retryConfig;
            return this;
        }

        public ClientConfig build() {
            return new ClientConfig(this);
        }
    }
}
