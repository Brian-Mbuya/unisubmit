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

    /**
     * ResponseStatusException must keep its HTTP status. Without this handler
     * the catch-all below would swallow it and answer REST/file requests with
     * an HTML redirect — breaking the document viewer's 404/403 responses and
     * any fetch() caller expecting a status code.
     */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<String> handleResponseStatus(
            org.springframework.web.server.ResponseStatusException ex) {
        return org.springframework.http.ResponseEntity
                .status(ex.getStatusCode())
                .body(ex.getReason() != null ? ex.getReason() : "");
    }

    /**
     * Real 404s (unmatched URLs, missing static resources) must reach the themed
     * templates/error/404.html — NOT the catch-all below, which would silently
     * bounce the user to the Referer (masking the 404, spamming the error log,
     * and risking a redirect loop when the referring page is the broken one).
     */
    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class})
    public String handleNoResource(Exception ex,
                                   HttpServletRequest request,
                                   jakarta.servlet.http.HttpServletResponse response) {
        log.warn("404 Not Found: {}", request.getRequestURI());
        response.setStatus(404);
        return "error/404";
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
