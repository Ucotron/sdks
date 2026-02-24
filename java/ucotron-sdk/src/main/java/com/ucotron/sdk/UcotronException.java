package com.ucotron.sdk;

/**
 * Base exception for all Ucotron SDK errors.
 */
public class UcotronException extends Exception {
    public UcotronException(String message) {
        super(message);
    }

    public UcotronException(String message, Throwable cause) {
        super(message, cause);
    }
}
