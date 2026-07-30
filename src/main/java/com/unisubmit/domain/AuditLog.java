package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * An append-only record of something that happened to a {@link Submission}
 * (created, new version uploaded, feedback given, status changed, relation added).
 * <p>
 * Phase 4 — Academic Memory. This table is append-only at the application layer:
 * there is intentionally no update or delete pathway exposed (not even to admins),
 * so the project's history cannot be quietly rewritten later. The entity has no
 * {@code @PreUpdate} hook and no mutators are called after creation.
 */
@Entity
@Getter
@Setter
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_logs_submission", columnList = "submission_id")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "submission_id")
    private Submission submission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    /** Human-readable detail, e.g. "Version 2 uploaded" or "Status changed to APPROVED". */
    @Column(nullable = false, length = 500)
    private String detail;

    /** The user who triggered the event. Nullable for system-generated entries. */
    @ManyToOne(optional = true)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
