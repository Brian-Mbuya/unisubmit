package com.unisubmit.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Centralized exception handler — converts typed service exceptions into
 * user-friendly flash messages and redirects the user back to the referring page.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Redirect back with a flash error for "not found" scenarios */
    @ExceptionHandler(SubmissionNotFoundException.class)
    public String handleNotFound(SubmissionNotFoundException ex,
                                  RedirectAttributes redirectAttributes,
                                  HttpServletRequest request) {
        log.warn("SubmissionNotFoundException: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:" + getReferer(request, "/");
    }

    /** Redirect back with a flash error for authorization failures */
    @ExceptionHandler(UnauthorizedException.class)
    public String handleUnauthorized(UnauthorizedException ex,
                                      RedirectAttributes redirectAttributes,
                                      HttpServletRequest request) {
        log.warn("UnauthorizedException: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:" + getReferer(request, "/");
    }

    /** Redirect back with a flash error for duplicate-entity violations */
    @ExceptionHandler(DuplicateEntityException.class)
    public String handleDuplicate(DuplicateEntityException ex,
                                   RedirectAttributes redirectAttributes,
                                   HttpServletRequest request) {
        log.warn("DuplicateEntityException: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:" + getReferer(request, "/");
    }

    /** Redirect back with a flash error for validation/argument failures */
    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgument(IllegalArgumentException ex,
                                        RedirectAttributes redirectAttributes,
                                        HttpServletRequest request) {
        log.warn("IllegalArgumentException: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:" + getReferer(request, "/");
    }

    /** Redirect back with a flash error for Spring Security access denials */
    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(AccessDeniedException ex,
                                      RedirectAttributes redirectAttributes,
                                      HttpServletRequest request) {
        log.warn("AccessDeniedException: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage",
                "You do not have permission to perform that action.");
        return "redirect:" + getReferer(request, "/");
    }

    /** Catch-all for unexpected errors — logs full stack trace */
    @ExceptionHandler(Exception.class)
    public String handleGeneral(Exception ex,
                                 RedirectAttributes redirectAttributes,
                                 HttpServletRequest request) {
        log.error("Unhandled exception on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        redirectAttributes.addFlashAttribute("errorMessage",
                "An unexpected error occurred. Please try again.");
        return "redirect:" + getReferer(request, "/");
    }

    private String getReferer(HttpServletRequest request, String fallback) {
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            // Strip host to get just the path, avoiding open-redirect
            try {
                java.net.URI uri = new java.net.URI(referer);
                String path = uri.getPath();
                String query = uri.getQuery();
                return path + (query != null ? "?" + query : "");
            } catch (Exception ignored) {}
        }
        return fallback;
    }
}
