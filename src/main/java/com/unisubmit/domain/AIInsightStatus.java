package com.unisubmit.domain;

/**
 * Processing lifecycle for the AI analysis pipeline.
 * <p>
 * PENDING     → Insight record created, async task not yet started
 * PROCESSING  → Async analysis in progress
 * COMPLETED   → Analysis done; summary + keywords populated
 * FAILED      → Extraction or analysis failed; errorMessage populated
 */
public enum AIInsightStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
