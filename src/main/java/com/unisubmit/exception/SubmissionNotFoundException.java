package com.unisubmit.exception;

public class SubmissionNotFoundException extends RuntimeException {
    public SubmissionNotFoundException(Long id) {
        super("Submission not found: " + id);
    }
    public SubmissionNotFoundException(String message) {
        super(message);
    }
}
