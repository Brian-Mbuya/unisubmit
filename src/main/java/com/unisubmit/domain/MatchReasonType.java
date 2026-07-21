package com.unisubmit.domain;

/**
 * Why Stage-1 shortlisted a pair. Drives the reason string on the partner card and the
 * per-reason acceptance-rate telemetry on the admin evaluation page.
 */
public enum MatchReasonType {
    /** Same problem space, different methods — the pairing this engine exists to find. */
    COMPLEMENT,
    /** Shared technologies / research areas — useful, but twins rather than complements. */
    OVERLAP,
    /** The partner's work is completed or their author is more senior. */
    MENTOR;

    public String label() {
        return switch (this) {
            case COMPLEMENT -> "Complement";
            case OVERLAP -> "Overlap";
            case MENTOR -> "Mentor";
        };
    }
}
