package com.unisubmit.service;

import com.unisubmit.domain.PasswordResetCode;
import com.unisubmit.domain.User;
import com.unisubmit.repository.PasswordResetCodeRepository;
import com.unisubmit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the reset-code lifecycle end to end against a real {@link BCryptPasswordEncoder}
 * (not a mock) so the hash/match assertions actually exercise the same code path
 * production uses — a mocked encoder that just echoes strings back would let a bug in
 * the real matches() call slip through unnoticed.
 */
class PasswordResetServiceTest {

    @Mock private PasswordResetCodeRepository codeRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private PasswordResetService service;
    private User user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new PasswordResetService(codeRepository, userRepository, passwordEncoder, emailService);

        user = new User();
        user.setId(42L);
        user.setUsername("lecturer@university.edu");
        user.setName("Dr. Smith");
    }

    @Test
    void requestCodeInvalidatesPriorCodesAndEmailsANewOne() {
        service.requestCode(user);

        verify(codeRepository).invalidateActiveCodes(42L);
        ArgumentCaptor<PasswordResetCode> saved = ArgumentCaptor.forClass(PasswordResetCode.class);
        verify(codeRepository).save(saved.capture());
        assertEquals(user, saved.getValue().getUser());
        assertTrue(saved.getValue().getExpiresAt().isAfter(Instant.now()));

        ArgumentCaptor<String> emailedCode = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetCode(eq(user), emailedCode.capture(), any(Duration.class));
        assertEquals(6, emailedCode.getValue().length());
        assertTrue(passwordEncoder.matches(emailedCode.getValue(), saved.getValue().getCodeHash()));
    }

    @Test
    void secondRequestWithinCooldownIsIgnored() {
        service.requestCode(user);
        service.requestCode(user);

        // Only the first request should have touched the repository or sent an email.
        verify(codeRepository, times(1)).invalidateActiveCodes(anyLong());
        verify(codeRepository, times(1)).save(any());
        verify(emailService, times(1)).sendPasswordResetCode(any(), any(), any());
    }

    @Test
    void correctCodeResetsThePassword() {
        String rawCode = "123456";
        PasswordResetCode entity = activeCodeFor(rawCode, Instant.now().plusSeconds(600));
        when(codeRepository.findFirstByUser_IdAndUsedFalseOrderByCreatedAtDesc(42L))
                .thenReturn(Optional.of(entity));

        PasswordResetService.ResetResult result = service.verifyAndReset(user, rawCode, "brandNewPass1");

        assertEquals(PasswordResetService.ResetResult.SUCCESS, result);
        assertTrue(entity.isUsed());
        assertTrue(passwordEncoder.matches("brandNewPass1", user.getPassword()));
        verify(userRepository).save(user);
    }

    @Test
    void wrongCodeIsRejectedAndCountsAsAnAttempt() {
        PasswordResetCode entity = activeCodeFor("123456", Instant.now().plusSeconds(600));
        when(codeRepository.findFirstByUser_IdAndUsedFalseOrderByCreatedAtDesc(42L))
                .thenReturn(Optional.of(entity));

        PasswordResetService.ResetResult result = service.verifyAndReset(user, "000000", "brandNewPass1");

        assertEquals(PasswordResetService.ResetResult.INVALID_CODE, result);
        assertEquals(1, entity.getAttempts());
        assertNotEquals("brandNewPass1", user.getPassword());
        verify(userRepository, never()).save(any());
    }

    @Test
    void expiredCodeIsRejectedEvenWhenCorrect() {
        String rawCode = "123456";
        PasswordResetCode entity = activeCodeFor(rawCode, Instant.now().minusSeconds(1));
        when(codeRepository.findFirstByUser_IdAndUsedFalseOrderByCreatedAtDesc(42L))
                .thenReturn(Optional.of(entity));

        PasswordResetService.ResetResult result = service.verifyAndReset(user, rawCode, "brandNewPass1");

        assertEquals(PasswordResetService.ResetResult.EXPIRED, result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void codeIsLockedOutAfterMaxAttempts() {
        PasswordResetCode entity = activeCodeFor("123456", Instant.now().plusSeconds(600));
        entity.setAttempts(5); // == MAX_VERIFY_ATTEMPTS
        when(codeRepository.findFirstByUser_IdAndUsedFalseOrderByCreatedAtDesc(42L))
                .thenReturn(Optional.of(entity));

        // Even the right code no longer works once the attempt budget is spent.
        PasswordResetService.ResetResult result = service.verifyAndReset(user, "123456", "brandNewPass1");

        assertEquals(PasswordResetService.ResetResult.LOCKED, result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void noActiveCodeIsRejected() {
        when(codeRepository.findFirstByUser_IdAndUsedFalseOrderByCreatedAtDesc(42L))
                .thenReturn(Optional.empty());

        PasswordResetService.ResetResult result = service.verifyAndReset(user, "123456", "brandNewPass1");

        assertEquals(PasswordResetService.ResetResult.NO_ACTIVE_CODE, result);
    }

    private PasswordResetCode activeCodeFor(String rawCode, Instant expiresAt) {
        PasswordResetCode entity = new PasswordResetCode();
        entity.setUser(user);
        entity.setCodeHash(passwordEncoder.encode(rawCode));
        entity.setExpiresAt(expiresAt);
        return entity;
    }
}
