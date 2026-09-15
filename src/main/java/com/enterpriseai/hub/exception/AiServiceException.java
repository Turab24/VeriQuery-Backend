package com.enterpriseai.hub.exception;

/**
 * Raised when the configured chat or embedding model cannot be reached or returns an
 * unusable response. Mapped to 503 so callers can distinguish an upstream AI outage
 * from a bug in the platform.
 */
public class AiServiceException extends ApiException {

    public AiServiceException(String message, Throwable cause) {
        super(ErrorCode.AI_SERVICE_ERROR, message, cause);
    }

    public AiServiceException(String message) {
        super(ErrorCode.AI_SERVICE_ERROR, message);
    }
}
