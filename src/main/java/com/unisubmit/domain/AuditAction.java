package com.unisubmit.domain;

/**
 * The kinds of events recorded on a submission's append-only activity history.
 * Phase 4 — Academic Memory.
 */
public enum AuditAction {
    SUBMISSION_CREATED,
    VERSION_UPLOADED,
    FEEDBACK_ADDED,
    STATUS_CHANGED,
    RELATION_ADDED
}
