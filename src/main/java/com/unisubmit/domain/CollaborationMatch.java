package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Phase 8 — a collaboration pairing between two submissions, kept SEPARATE from
 * {@link SubmissionSimilarity} (which stays for integrity/plagiarism detection).
 * <p>
 * Where SubmissionSimilarity says "how alike are these?", CollaborationMatch says
 * "would these two students gain from working together?" — the opposite signal
 * set (same-unit is a red flag for integrity but worthless for collaboration).
 * <p>
 * Pairs are stored ONCE in canonical order (submissionA.id &lt; submissionB.id).
 * The gains fields are directional (A's gain vs B's gain); the UI renders
 * "what you gain" relative to whoever is viewing.
 * <p>
 * The two hash columns fingerprint each side's insight text so Stage 2 (the
 * expensive LLM assessment) only re-runs when a submission's analysis actually
 * changed — not on every recompute.
 */
@Entity
@Getter
@Setter
@Table(name = "collaboration_matches", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"submission_a_id", "submission_b_id"})
})
public class CollaborationMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "submission_a_id")
    private Submission submissionA;

    @ManyToOne(optional = false)
    @JoinColumn(name = "submission_b_id")
    private Submission submissionB;

    /** Stage 1 mechanical pre-filter score (semantic + tech + area + domain; unit excluded). */
    @Column(name = "mechanical_score", nullable = false)
    private Double mechanicalScore = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "collaboration_value", nullable = false, length = 20)
    private CollaborationValue collaborationValue = CollaborationValue.UNASSESSED;

    @Enumerated(EnumType.STRING)
    @Column(name = "collaboration_type", nullable = false, length = 20)
    private CollaborationType collaborationType = CollaborationType.UNKNOWN;

    /** What submissionA's student gains (directional). */
    @Column(name = "what_a_gains", columnDefinition = "TEXT")
    private String whatAGains;

    /** What submissionB's student gains (directional). */
    @Column(name = "what_b_gains", columnDefinition = "TEXT")
    private String whatBGains;

    /** 2-sentence natural-language pitch (viewer-neutral). */
    @Column(columnDefinition = "TEXT")
    private String pitch;

    /** Specific limitations in one project the other addresses. */
    @Column(name = "complementary_gaps", columnDefinition = "TEXT")
    private String complementaryGaps;

    /** Insight fingerprints — Stage 2 skips re-assessment when both are unchanged. */
    @Column(name = "hash_a", length = 64)
    private String hashA;

    @Column(name = "hash_b", length = 64)
    private String hashB;

    @Column(name = "computed_at")
    private LocalDateTime computedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** True once the LLM has run (or was skipped for lack of a key). */
    public boolean isAssessed() {
        return collaborationValue != CollaborationValue.UNASSESSED;
    }
}
