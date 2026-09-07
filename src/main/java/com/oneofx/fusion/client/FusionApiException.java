package com.oneofx.fusion.client;

public class FusionApiException extends RuntimeException {
    private final int statusCode;

    public FusionApiException(String message) {
        this(message, -1, null);
    }

    public FusionApiException(String message, Throwable cause) {
        this(message, -1, cause);
    }

    public FusionApiException(String message, int statusCode) {
        this(message, statusCode, null);
    }

    private FusionApiException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
