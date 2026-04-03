package com.chuka.irir.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * JPA entity representing a supervisor or directorate member's review of a {@link Project}.
 *
 * Each review captures:
 * <ul>
 *   <li>The reviewer (a {@link User} with SUPERVISOR or DIRECTORATE role)</li>
 *   <li>The decision ({@link ReviewDecision}: APPROVED, REJECTED, REVISION_REQUESTED)</li>
 *   <li>Written comments / feedback for the student</li>
 * </ul>
 *
 * <p>A project can have multiple reviews over its lifecycle (e.g., initial review,
 * revision review, final approval).</p>
 */
@Entity
@Table(name = "reviews")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The project being reviewed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "project_id")
    private Project project;

    /** Generic submission being reviewed for final-year or coursework workflows. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "submission_id")
    private Submission submission;

    /** The supervisor/directorate member who conducted the review. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    /** The reviewer's decision on the project. */
    @NotNull(message = "Review decision is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private ReviewDecision decision;

    /** Written feedback / comments from the reviewer. */
    @Column(columnDefinition = "TEXT")
    private String comments;

    /** Timestamp when the review was submitted. */
    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private LocalDateTime reviewedAt;

    @PrePersist
    protected void onCreate() {
        this.reviewedAt = LocalDateTime.now();
    }
}
