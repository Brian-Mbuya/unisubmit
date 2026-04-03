package com.chuka.irir.model;

/**
 * Enumeration of possible states in the project submission lifecycle.
 *
 * <ul>
 *   <li><b>DRAFT</b>        — Initial state; student is still editing the submission</li>
 *   <li><b>SUBMITTED</b>    — Student has submitted for review</li>
 *   <li><b>UNDER_REVIEW</b> — A supervisor/directorate member is currently reviewing</li>
 *   <li><b>APPROVED</b>     — Project has been approved</li>
 *   <li><b>REJECTED</b>     — Project has been rejected (with feedback)</li>
 *   <li><b>FLAGGED</b>      — System flagged for high similarity or policy violation</li>
 *   <li><b>INCUBATION</b>   — Directorate has flagged for industry incubation</li>
 * </ul>
 */
public enum ProjectStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    PENDING,
    APPROVED,
    REJECTED,
    FLAGGED,
    INCUBATION
}
