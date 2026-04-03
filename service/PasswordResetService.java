package com.chuka.irir.service;

import com.chuka.irir.exception.ResourceNotFoundException;
import com.chuka.irir.model.PasswordResetToken;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.PasswordResetTokenRepository;
import com.chuka.irir.repository.UserRepository;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class PasswordResetService {

    private final JavaMailSender mailSender;
    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String applicationBaseUrl;

    public PasswordResetService(JavaMailSender mailSender,
                                PasswordResetTokenRepository tokenRepository,
                                UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.base-url:http://localhost:8080}") String applicationBaseUrl) {
        this.mailSender = mailSender;
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.applicationBaseUrl = normalizeBaseUrl(applicationBaseUrl);
    }

    /**
     * Full flow: validate email → delete old tokens → generate new token → send email.
     */
    @Transactional
    public void initiatePasswordReset(String email) {
        String normalizedEmail = normalizeRequiredText(email, "Email is required.");
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", normalizedEmail));

        // Delete any existing unused tokens for this user
        tokenRepository.deleteAllByUserId(user.getId());

        // Generate and save new token
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken(token, user);
        tokenRepository.save(resetToken);

        // Send email
        sendResetEmail(user, token);
    }

    /**
     * Validates token and updates the user's password.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        String normalizedToken = normalizeRequiredText(token, "Reset token is required.");
        String normalizedPassword = normalizeRequiredText(newPassword, "New password is required.");

        PasswordResetToken resetToken = tokenRepository.findByToken(normalizedToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token."));

        if (resetToken.isExpired()) {
            tokenRepository.delete(resetToken);
            throw new IllegalArgumentException("Reset token has expired. Please request a new one.");
        }

        if (resetToken.isUsed()) {
            throw new IllegalArgumentException("This reset link has already been used.");
        }

        // Update password
        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(normalizedPassword));
        userRepository.save(user);

        // Mark token as used (don't delete — keep for audit trail)
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
    }

    /**
     * Checks if a token is valid (exists, not expired, not used).
     * Used by the controller to validate before showing the reset form.
     */
    public boolean isTokenValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return tokenRepository.findByToken(token)
                .map(t -> !t.isExpired() && !t.isUsed())
                .orElse(false);
    }

    /**
     * Sends the password reset email with a clickable link.
     */
    private void sendResetEmail(User user, String token) {
        String resetLink = applicationBaseUrl + "/reset-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("IRIR — Password Reset Request");
        message.setText(
                "Hello " + user.getFirstName() + ",\n\n" +
                "We received a request to reset your IRIR account password.\n\n" +
                "Click the link below to reset your password:\n" +
                resetLink + "\n\n" +
                "This link expires in 24 hours.\n\n" +
                "If you did not request this, please ignore this email — " +
                "your password will remain unchanged.\n\n" +
                "Regards,\nIRIR Team"
        );

        mailSender.send(message);
    }

    public String getApplicationBaseUrl() {
        return applicationBaseUrl;
    }

    private String normalizeRequiredText(String value, String message) {
        String normalizedValue = Objects.requireNonNullElse(value, "").trim();
        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalizedValue;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = normalizeRequiredText(baseUrl, "Application base URL is required.");
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    /**
     * Scheduled cleanup: removes expired tokens from DB every 24 hours.
     * Runs automatically — no manual call needed.
     */
    @Scheduled(fixedRate = 86400000) // every 24 hours in milliseconds
    @Transactional
    public void purgeExpiredTokens() {
        tokenRepository.deleteAllExpiredTokens(LocalDateTime.now());
    }
}
