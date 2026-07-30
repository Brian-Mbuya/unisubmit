package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * Stores the result of the AI analysis pipeline for a Submission.
 * <p>
 * Lifecycle: PENDING → PROCESSING → COMPLETED | FAILED
 * <p>
 * The keywords field uses @ElementCollection so they are stored as
 * individual rows, allowing direct set-intersection queries later.
 * The summary and errorMessage use TEXT columns for unbounded length.
 */
@Entity
@Getter
@Setter
@Table(name = "ai_insights")
public class AIInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToOne(optional = false)
    @JoinColumn(name = "submission_id", unique = true)
    private Submission submission;

    /** AI-generated extractive summary (top scored sentences). */
    @Column(columnDefinition = "TEXT")
    private String summary;

    /**
     * Top-N keywords extracted by term frequency from the document body.
     * Stored as separate rows; never stored as a comma-separated string.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ai_insight_keywords", joinColumns = @JoinColumn(name = "insight_id"))
    @Column(name = "keyword")
    private Set<String> keywords = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AIInsightStatus status = AIInsightStatus.PENDING;

    /** Populated only when status == FAILED. Max ~2000 chars. */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "problem_statement", columnDefinition = "TEXT")
    private String problemStatement;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ai_insight_objectives", joinColumns = @JoinColumn(name = "insight_id"))
    @Column(name = "objective", columnDefinition = "TEXT")
    private Set<String> objectives = new LinkedHashSet<>();

    /**
     * Phase 8 — broad real-world application domains (healthcare, transportation,
     * agriculture, energy, education, etc.). Deliberately cross-disciplinary so a
     * CS traffic-ML project and a Civil traffic-lights project can be matched on
     * "transportation" despite sharing zero keywords or units.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ai_insight_domains", joinColumns = @JoinColumn(name = "insight_id"))
    @Column(name = "domain")
    private Set<String> problemDomains = new LinkedHashSet<>();
}
