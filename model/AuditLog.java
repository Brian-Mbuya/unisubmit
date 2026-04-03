package com.chuka.irir.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * JPA entity for recording system audit events.
 *
 * Tracks user actions throughout the application for security monitoring,
 * compliance, and troubleshooting. Used by the System Administrator (ADMIN role)
 * to review system activity logs.
 *
 * <p>Examples of audited actions:
 * <ul>
 *   <li>USER_LOGIN, USER_LOGOUT, USER_REGISTERED</li>
 *   <li>PROJECT_SUBMITTED, PROJECT_APPROVED, PROJECT_REJECTED</li>
 *   <li>SIMILARITY_CHECK_COMPLETED, PROJECT_FLAGGED</li>
 *   <li>USER_ROLE_CHANGED, USER_DISABLED</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "audit_logs")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user who performed the action. Nullable for system-generated events. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** Type of action performed (e.g., "USER_LOGIN", "PROJECT_SUBMITTED"). */
    @Column(nullable = false, length = 100)
    private String action;

    /** Additional details or context about the action. */
    @Column(columnDefinition = "TEXT")
    private String details;

    /** Entity type that was affected (e.g., "Project", "User"). */
    @Column(name = "entity_type", length = 50)
    private String entityType;

    /** ID of the entity that was affected. */
    @Column(name = "entity_id")
    private Long entityId;

    /** IP address of the client that initiated the action. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /** Timestamp when the action occurred. */
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        this.timestamp = LocalDateTime.now();
    }
}
