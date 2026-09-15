package com.enterpriseai.hub.common;

import com.enterpriseai.hub.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * The single error envelope returned by every endpoint.
 */
@Schema(name = "ApiError", description = "Standard error envelope")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        @Schema(example = "2026-01-14T10:15:30Z") Instant timestamp,
        @Schema(example = "404") int status,
        @Schema(example = "DOCUMENT_NOT_FOUND") String error,
        @Schema(example = "Document 123 was not found") String message,
        @Schema(example = "/api/documents/123") String path,
        @Schema(description = "Correlation id, also returned in the X-Request-Id header") String requestId,
        @Schema(description = "Field level validation failures") List<FieldViolation> details) {

    public record FieldViolation(String field, String message) {
    }

    public static ApiErrorResponse of(ErrorCode code, String message, String path, String requestId) {
        return new ApiErrorResponse(Instant.now(), code.status().value(), code.name(), message, path, requestId, null);
    }

    public static ApiErrorResponse of(ErrorCode code,
                                      String message,
                                      String path,
                                      String requestId,
                                      List<FieldViolation> details) {
        return new ApiErrorResponse(Instant.now(), code.status().value(), code.name(), message, path, requestId, details);
    }
}
