package com.unisubmit.domain;

/**
 * Processing lifecycle for the AI analysis pipeline.
 * <p>
 * PENDING     → Insight record created, async task not yet started
 * PROCESSING  → Async analysis in progress
 * COMPLETED   → Analysis done via the LLM; summary + keywords populated
 * DEGRADED    → Analysis done via the heuristic fallback (no key / provider down);
 *               genuine document-derived summary + keywords, but no LLM enrichment.
 *               Displayable and retryable; accepted alongside COMPLETED by matching/search.
 * FAILED      → Extraction or analysis failed; errorMessage populated
 */
public enum AIInsightStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    DEGRADED,
    FAILED;

    /**
     * True when the insight carries genuine document-derived content (summary + keywords) —
     * i.e. COMPLETED (via LLM) or DEGRADED (via heuristic). Read-gates for matching, search,
     * discovery, and analytics use this so a no-key deployment still surfaces new work.
     * (The embedding backfill deliberately does NOT use this — it excludes DEGRADED.)
     */
    public boolean hasContent() {
        return this == COMPLETED || this == DEGRADED;
    }
}
