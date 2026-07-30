package com.unisubmit.service;

import com.unisubmit.domain.PasswordResetCode;
import com.unisubmit.domain.User;
import com.unisubmit.repository.PasswordResetCodeRepository;
import com.unisubmit.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Email-delivered password reset for lecturers/admins (their username IS their email —
 * students have no email on file and stay on the existing admin-mediated
 * {@code /forgot-password} flow; see {@link com.unisubmit.controller.AuthController}).
 * <p>
 * A 6-digit code, hashed with the account {@link PasswordEncoder} (never stored in
 * plaintext), single-use, expires after {@link #CODE_TTL}, and locks out after
 * {@link #MAX_VERIFY_ATTEMPTS} wrong guesses on that code. Requesting a new code
 * invalidates whatever was issued before it, so at most one code is ever live per user.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    static final Duration CODE_TTL = Duration.ofMinutes(15);
    static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);
    static final int MAX_VERIFY_ATTEMPTS = 5;

    public enum ResetResult { SUCCESS, NO_ACTIVE_CODE, EXPIRED, INVALID_CODE, LOCKED }

    private final PasswordResetCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    /**
     * Per-user request cooldown. In-memory (single-node tradeoff, same as
     * {@code LoginAttemptService}) — resets on redeploy, which is fine: worst case
     * someone can request a fresh code immediately after a deploy.
     */
    private final Map<Long, Instant> lastRequestAt = new ConcurrentHashMap<>();

    public PasswordResetService(PasswordResetCodeRepository codeRepository,
                                UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService) {
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    /**
     * Generates, stores and emails a fresh code for {@code user}. Silently ignores
     * repeat requests inside {@link #REQUEST_COOLDOWN} rather than erroring, so a
     * double-click or an impatient retry doesn't spam the inbox or burn through the
     * verify-attempt budget on a code the user hasn't seen yet.
     */
    @Transactional
    public void requestCode(User user) {
        Instant last = lastRequestAt.get(user.getId());
        if (last != null && last.plus(REQUEST_COOLDOWN).isAfter(Instant.now())) {
            log.info("Password reset re-request for user {} ignored (cooldown active)", user.getId());
            return;
        }
        codeRepository.invalidateActiveCodes(user.getId());

        String code = generateCode();
        PasswordResetCode entity = new PasswordResetCode();
        entity.setUser(user);
        entity.setCodeHash(passwordEncoder.encode(code));
        entity.setExpiresAt(Instant.now().plus(CODE_TTL));
        codeRepository.save(entity);

        lastRequestAt.put(user.getId(), Instant.now());
        emailService.sendPasswordResetCode(user, code, CODE_TTL);
    }

    /**
     * Verifies {@code code} against the active code for {@code user} and, on success,
     * sets {@code newPassword}. The caller is responsible for turning anything other
     * than {@code SUCCESS} into one generic "invalid or expired code" message — the
     * distinct results exist for logging/testing, not for the end user, so a wrong
     * guess can't be used to tell an expired code from a still-live one.
     */
    @Transactional
    public ResetResult verifyAndReset(User user, String code, String newPassword) {
        Optional<PasswordResetCode> active =
                codeRepository.findFirstByUser_IdAndUsedFalseOrderByCreatedAtDesc(user.getId());
        if (active.isEmpty()) {
            return ResetResult.NO_ACTIVE_CODE;
        }
        PasswordResetCode entity = active.get();
        if (entity.getExpiresAt().isBefore(Instant.now())) {
            return ResetResult.EXPIRED;
        }
        if (entity.getAttempts() >= MAX_VERIFY_ATTEMPTS) {
            return ResetResult.LOCKED;
        }

        String submitted = code == null ? "" : code.trim();
        if (submitted.isEmpty() || !passwordEncoder.matches(submitted, entity.getCodeHash())) {
            entity.setAttempts(entity.getAttempts() + 1);
            codeRepository.save(entity);
            return ResetResult.INVALID_CODE;
        }

        entity.setUsed(true);
        codeRepository.save(entity);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        lastRequestAt.remove(user.getId());
        return ResetResult.SUCCESS;
    }

    private String generateCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
