package com.ucotron.sdk;

/**
 * Exception thrown when all retry attempts have been exhausted.
 */
public class UcotronRetriesExhaustedException extends UcotronException {
    private final int attempts;

    public UcotronRetriesExhaustedException(int attempts, Throwable lastError) {
        super("All " + attempts + " retry attempts exhausted", lastError);
        this.attempts = attempts;
    }

    public int getAttempts() { return attempts; }
}
