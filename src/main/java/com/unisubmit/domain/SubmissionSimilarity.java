package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "submission_similarities", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"submission_a_id", "submission_b_id"})
}, indexes = {
    @Index(name = "ix_simil_b", columnList = "submission_b_id")
})
public class SubmissionSimilarity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "submission_a_id")
    private Submission submissionA;

    @ManyToOne(optional = false)
    @JoinColumn(name = "submission_b_id")
    private Submission submissionB;

    @Column(nullable = false)
    private Double similarityScore;

    @ElementCollection
    @CollectionTable(name = "similarity_keywords", joinColumns = @JoinColumn(name = "similarity_id"))
    @Column(name = "keyword")
    private List<String> matchedKeywords;

    /** Technology tag names shared by both submissions (structured knowledge-model overlap). */
    @ElementCollection
    @CollectionTable(name = "similarity_technologies", joinColumns = @JoinColumn(name = "similarity_id"))
    @Column(name = "technology")
    private List<String> matchedTechnologies;

    /** ResearchArea tag names shared by both submissions. */
    @ElementCollection
    @CollectionTable(name = "similarity_research_areas", joinColumns = @JoinColumn(name = "similarity_id"))
    @Column(name = "research_area")
    private List<String> matchedResearchAreas;

    // ── Per-signal breakdown (each 0..1) so the UI can explain "why this match" ──
    @Column(name = "keyword_score")
    private Double keywordScore;

    @Column(name = "title_score")
    private Double titleScore;

    @Column(name = "unit_score")
    private Double unitScore;

    @Column(name = "semantic_score")
    private Double semanticScore;

    @Column(name = "technology_score")
    private Double technologyScore;

    @Column(name = "research_area_score")
    private Double researchAreaScore;

    @Column(name = "same_unit")
    private Boolean sameUnit;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
