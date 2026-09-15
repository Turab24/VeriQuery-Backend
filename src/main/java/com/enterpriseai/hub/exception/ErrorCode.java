package com.enterpriseai.hub.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error identifiers. Clients branch on these rather than on
 * human readable messages, so messages can be reworded without breaking integrations.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT),
    DUPLICATE_DOCUMENT(HttpStatus.CONFLICT),
    CONFLICT(HttpStatus.CONFLICT),
    DOCUMENT_NOT_READY(HttpStatus.CONFLICT),
    EMPTY_FILE(HttpStatus.BAD_REQUEST),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    TEXT_EXTRACTION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY),
    STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    AI_SERVICE_ERROR(HttpStatus.SERVICE_UNAVAILABLE),
    VECTOR_SEARCH_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
