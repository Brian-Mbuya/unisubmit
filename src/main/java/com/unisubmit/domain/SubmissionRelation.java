package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A manually curated lineage link between two submissions — e.g.
 * "submissionA EXTENDS submissionB". Distinct from the automatic, keyword/vector
 * based {@link SubmissionSimilarity}: relations are deliberate, staff-curated
 * statements about research genealogy.
 * <p>
 * Phase 4 — Academic Memory. Created by lecturers and admins only.
 */
@Entity
@Getter
@Setter
@Table(name = "submission_relations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"submission_a_id", "submission_b_id", "relation_type"})
})
public class SubmissionRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "submission_a_id")
    private Submission submissionA;

    @ManyToOne(optional = false)
    @JoinColumn(name = "submission_b_id")
    private Submission submissionB;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 30)
    private RelationType relationType;

    @ManyToOne(optional = true)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
