package com.bonitasoft.connectors.whatsapp;

public class WhatsAppException extends RuntimeException {

    private final int statusCode;
    private final boolean retryable;
    private final int errorCode;

    public WhatsAppException(String message, int statusCode, boolean retryable) {
        this(message, statusCode, 0, retryable);
    }

    public WhatsAppException(String message, int statusCode, int errorCode, boolean retryable) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public WhatsAppException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.errorCode = 0;
        this.retryable = false;
    }

    public int getStatusCode() { return statusCode; }
    public boolean isRetryable() { return retryable; }
    public int getErrorCode() { return errorCode; }
}
