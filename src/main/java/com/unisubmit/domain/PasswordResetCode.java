package com.unisubmit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A one-time password-reset code, emailed to lecturers/admins (students have no email —
 * see {@code UserService.createStudent}). Hashed with the same {@code PasswordEncoder} as
 * account passwords: the raw 6-digit code only ever exists in memory and in the outgoing
 * email, never in the database.
 */
@Entity
@Getter
@Setter
@Table(name = "password_reset_codes")
public class PasswordResetCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    /** Failed verify attempts against THIS code — locked out after PasswordResetService.MAX_VERIFY_ATTEMPTS. */
    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
