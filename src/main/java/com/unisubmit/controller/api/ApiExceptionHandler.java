package com.unisubmit.controller.api;

import com.unisubmit.exception.DuplicateEntityException;
import com.unisubmit.exception.SubmissionNotFoundException;
import com.unisubmit.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

/**
 * JSON error handling for {@code /api/v1/**}.
 * <p>
 * <b>Why this exists.</b> {@code GlobalExceptionHandler} is a catch-all
 * {@code @ControllerAdvice} whose handlers return {@code "redirect:" + referer} — correct
 * for a browser posting a form, useless to a Flutter client, which would receive a 302 to
 * an HTML page and no way to tell success from failure. (The existing
 * {@code ResponseStatusException} handler over there was added for exactly this reason,
 * scoped to one exception type.)
 * <p>
 * This advice is restricted by {@code basePackages} to the API controllers and given
 * {@code HIGHEST_PRECEDENCE}, so it is consulted first for those handlers while every
 * Thymeleaf controller keeps the redirect-and-flash behaviour untouched.
 */
@RestControllerAdvice(basePackages = "com.unisubmit.controller.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ApiError(String error, String message) {}

    @ExceptionHandler(SubmissionNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(SubmissionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("not_found", ex.getMessage()));
    }

    @ExceptionHandler({UnauthorizedException.class, AccessDeniedException.class})
    public ResponseEntity<ApiError> handleForbidden(Exception ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("forbidden", "You do not have permission to perform that action."));
    }

    @ExceptionHandler(DuplicateEntityException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateEntityException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("conflict", ex.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception ex) {
        String message = (ex instanceof MethodArgumentNotValidException)
                ? "Request body failed validation."
                : ex.getMessage();
        return ResponseEntity.badRequest().body(new ApiError("bad_request", message));
    }

    /**
     * Catch-all. Logs the stack trace (same as the browser handler) but answers with a
     * generic 500 body — internal messages are not leaked to the mobile client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Unhandled API exception on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("server_error", "An unexpected error occurred. Please try again."));
    }
}
