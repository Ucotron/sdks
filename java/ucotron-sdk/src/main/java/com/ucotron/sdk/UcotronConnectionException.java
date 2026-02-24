package com.ucotron.sdk;

/**
 * Exception for network/connection errors.
 */
public class UcotronConnectionException extends UcotronException {
    public UcotronConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
