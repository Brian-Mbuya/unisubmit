package com.unisubmit.service;

import com.unisubmit.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Thin wrapper over {@link JavaMailSender} for password-reset codes (lecturers/admins
 * only — students have no email, see {@code UserService.createStudent}).
 * <p>
 * {@code spring.mail.host} is always set (see application.yml) purely so Spring Boot
 * creates the {@code JavaMailSender} bean; whether a send is actually attempted is
 * gated on {@code spring.mail.username} being non-blank, mirroring the {@code NO_KEY}
 * pattern already used for the AI integration. Unconfigured (e.g. tests, untouched
 * local dev) logs the code instead of emailing it — never throws, never dials out.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final boolean configured;

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.username:}") String mailUsername,
                        @Value("${app.mail.from:${spring.mail.username:no-reply@unisubmit.local}}") String from) {
        this.mailSender = mailSender;
        this.from = from;
        this.configured = mailUsername != null && !mailUsername.isBlank();
        if (!configured) {
            log.warn("MAIL_USERNAME is not set — outbound email is DISABLED. Self-service "
                    + "password reset will fall back to notifying admins instead of emailing "
                    + "a code. Set MAIL_USERNAME/MAIL_PASSWORD to enable it.");
        }
    }

    /**
     * Whether outbound mail can actually be sent.
     * <p>
     * Callers must branch on this rather than assuming delivery: without it
     * {@code /forgot-password} redirected to "we sent you a code" while this class had only
     * written the code to the log — an auth flow reporting success on a send that never
     * happened, for the two roles that can genuinely be locked out.
     */
    public boolean isConfigured() {
        return configured;
    }

    /** Fire-and-forget: the caller (a form POST) must not block on an SMTP round trip. */
    @Async
    public void sendPasswordResetCode(User user, String code, Duration ttl) {
        String to = user.getUsername();
        if (!configured) {
            log.warn("MAIL not configured (spring.mail.username unset) — password reset code for {} "
                    + "would have been emailed. Code: {}", to, code);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Your UniSubmit password reset code");
        message.setText("Hi " + user.getName() + ",\n\n"
                + "Your password reset code is: " + code + "\n\n"
                + "It expires in " + ttl.toMinutes() + " minutes and can only be used once. "
                + "If you didn't request this, you can safely ignore this email.\n\n"
                + "— UniSubmit");
        try {
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Failed to send password reset email to {}: {}", to, ex.getMessage());
        }
    }
}
