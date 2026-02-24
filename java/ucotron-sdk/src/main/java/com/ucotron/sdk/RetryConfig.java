package com.ucotron.sdk;

import java.time.Duration;

/**
 * Configuration for retry behavior with exponential backoff.
 */
public class RetryConfig {
    private final int maxRetries;
    private final Duration baseDelay;
    private final Duration maxDelay;

    public RetryConfig(int maxRetries, Duration baseDelay, Duration maxDelay) {
        this.maxRetries = maxRetries;
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
    }

    public static RetryConfig defaults() {
        return new RetryConfig(3, Duration.ofMillis(100), Duration.ofSeconds(5));
    }

    public int getMaxRetries() { return maxRetries; }
    public Duration getBaseDelay() { return baseDelay; }
    public Duration getMaxDelay() { return maxDelay; }
}
