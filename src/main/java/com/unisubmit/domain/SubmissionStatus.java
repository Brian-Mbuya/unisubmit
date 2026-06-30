package com.unisubmit.domain;

public enum SubmissionStatus {
    // Legacy / general statuses
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,

    // Project lifecycle statuses (Phase 1)
    PROPOSAL,
    UNDER_REVIEW,
    FINAL,
    ARCHIVED
}
