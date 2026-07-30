package com.unisubmit.domain;

/**
 * Phase 8 — how worthwhile the LLM judged a collaboration pairing to be.
 * UNASSESSED is the pre-LLM state (Stage 1 shortlisted, Stage 2 not yet run or
 * no API key configured). Only HIGH and MEDIUM are surfaced to students.
 */
public enum CollaborationValue {
    HIGH,
    MEDIUM,
    LOW,
    NONE,
    UNASSESSED
}
