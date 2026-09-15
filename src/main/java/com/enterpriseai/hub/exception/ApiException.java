package com.enterpriseai.hub.exception;

import lombok.Getter;

/**
 * Base class for every exception the API deliberately translates into an HTTP response.
 * Anything that does not extend this is treated as an unexpected failure and reported
 * as {@link ErrorCode#INTERNAL_ERROR} without leaking internals to the client.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
