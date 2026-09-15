package com.enterpriseai.hub.exception;

import com.enterpriseai.hub.common.ApiErrorResponse;
import com.enterpriseai.hub.observability.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;

/**
 * Translates every exception into the {@link ApiErrorResponse} envelope.
 *
 * <p>Client errors are logged at WARN with the correlation id only; server errors are
 * logged at ERROR with the full stack trace. Internal details (SQL, stack traces, driver
 * messages) are never returned to the caller.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        if (code.status().is5xxServerError()) {
            log.error("{} while handling {} {}", code, request.getMethod(), request.getRequestURI(), ex);
        } else {
            log.warn("{} while handling {} {}: {}", code, request.getMethod(), request.getRequestURI(), ex.getMessage());
        }
        return build(code, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                            HttpServletRequest request) {
        List<ApiErrorResponse.FieldViolation> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        log.warn("Validation failed for {} {}: {} field(s)", request.getMethod(), request.getRequestURI(), details.size());
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR,
                        "Request validation failed",
                        request.getRequestURI(),
                        RequestContext.currentRequestId(),
                        details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                     HttpServletRequest request) {
        List<ApiErrorResponse.FieldViolation> details = ex.getConstraintViolations().stream()
                .map(violation -> new ApiErrorResponse.FieldViolation(
                        String.valueOf(violation.getPropertyPath()), violation.getMessage()))
                .toList();
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR,
                        "Request validation failed",
                        request.getRequestURI(),
                        RequestContext.currentRequestId(),
                        details));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiErrorResponse> handleMalformed(Exception ex, HttpServletRequest request) {
        log.warn("Malformed request {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.MALFORMED_REQUEST, "The request could not be parsed", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex,
                                                                HttpServletRequest request) {
        log.warn("Upload rejected on {}: exceeds configured multipart limit", request.getRequestURI());
        return build(ErrorCode.FILE_TOO_LARGE, "The uploaded file exceeds the maximum allowed size", request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex,
                                                                 HttpServletRequest request) {
        return build(ErrorCode.INVALID_CREDENTIALS, "Invalid e-mail address or password", request);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleDisabled(DisabledException ex, HttpServletRequest request) {
        return build(ErrorCode.ACCOUNT_DISABLED, "This account has been disabled", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex,
                                                                 HttpServletRequest request) {
        return build(ErrorCode.UNAUTHENTICATED, "Authentication is required to access this resource", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                              HttpServletRequest request) {
        log.warn("Access denied for {} {}", request.getMethod(), request.getRequestURI());
        return build(ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                     HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ApiErrorResponse(java.time.Instant.now(),
                        HttpStatus.METHOD_NOT_ALLOWED.value(),
                        "METHOD_NOT_ALLOWED",
                        "HTTP method " + ex.getMethod() + " is not supported for this endpoint",
                        request.getRequestURI(),
                        RequestContext.currentRequestId(),
                        null));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoHandler(NoHandlerFoundException ex, HttpServletRequest request) {
        return build(ErrorCode.RESOURCE_NOT_FOUND, "No endpoint matches this path", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleIntegrity(DataIntegrityViolationException ex,
                                                            HttpServletRequest request) {
        log.error("Database constraint violated on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(ErrorCode.CONFLICT, "The operation conflicts with existing data", request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        log.error("Database failure on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(ErrorCode.DATABASE_ERROR, "A database error prevented the request from completing", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiErrorResponse> build(ErrorCode code, String message, HttpServletRequest request) {
        return ResponseEntity.status(code.status())
                .body(ApiErrorResponse.of(code, message, request.getRequestURI(), RequestContext.currentRequestId()));
    }
}
