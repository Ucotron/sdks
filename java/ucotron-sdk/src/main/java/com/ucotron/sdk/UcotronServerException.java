package com.ucotron.sdk;

/**
 * Exception for server-side errors (4xx and 5xx HTTP responses).
 */
public class UcotronServerException extends UcotronException {
    private final int statusCode;

    public UcotronServerException(int statusCode, String message) {
        super("HTTP " + statusCode + ": " + message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() { return statusCode; }
}
