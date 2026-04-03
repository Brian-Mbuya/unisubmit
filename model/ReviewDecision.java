package com.chuka.irir.model;

/**
 * Enumeration of possible review decisions a supervisor or directorate member
 * can make when evaluating a submitted project.
 *
 * <ul>
 *   <li><b>APPROVED</b>          — Project meets all requirements</li>
 *   <li><b>REJECTED</b>          — Project does not meet requirements</li>
 *   <li><b>REVISION_REQUESTED</b> — Revisions needed before re-review</li>
 * </ul>
 */
public enum ReviewDecision {
    APPROVED,
    REJECTED,
    REVISION_REQUESTED
}
