package com.unisubmit.domain;

/**
 * Phase 8 — the kind of value a collaboration pairing offers, as classified by
 * the LLM. UNKNOWN covers pre-assessment and unparseable responses.
 */
public enum CollaborationType {
    MENTORSHIP,        // an upperclassman who completed similar work can guide a junior
    SKILL_EXCHANGE,    // one has ML expertise, the other domain knowledge / field data
    INTERDISCIPLINARY, // different departments, same real-world problem
    SCALE_UP,          // one built a prototype, the other has a real deployment env
    DATA_SHARING,      // one collected a dataset the other needs
    UNKNOWN;

    /** Lenient parse of the LLM's snake/loose string; never throws. */
    public static CollaborationType fromLoose(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        String norm = raw.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        for (CollaborationType t : values()) {
            if (t.name().equals(norm)) {
                return t;
            }
        }
        return UNKNOWN;
    }

    /** Human-facing label for the UI. */
    public String label() {
        return switch (this) {
            case MENTORSHIP -> "Mentorship";
            case SKILL_EXCHANGE -> "Skill exchange";
            case INTERDISCIPLINARY -> "Interdisciplinary";
            case SCALE_UP -> "Scale-up";
            case DATA_SHARING -> "Data sharing";
            case UNKNOWN -> "Collaboration";
        };
    }
}
