package com.unisubmit.security;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory brute-force protection for the login form.
 * <p>
 * Attempts are counted per {@code username|clientIp} key: after
 * {@value #MAX_ATTEMPTS} consecutive failures the key is locked for
 * {@link #LOCK_DURATION}. A successful login clears the counter.
 * State is in-memory only (single-node deployment) and expires naturally.
 */
@Service
public class LoginAttemptService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private static class Attempt {
        volatile int failures;
        volatile Instant firstFailure = Instant.now();
        volatile Instant lockedUntil;
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public static String key(String username, String clientIp) {
        String user = username == null ? "" : username.trim().toLowerCase();
        return user + "|" + (clientIp == null ? "?" : clientIp);
    }

    public void loginFailed(String key) {
        Attempt attempt = attempts.computeIfAbsent(key, k -> new Attempt());
        synchronized (attempt) {
            // Stale window: restart counting after the lock period has elapsed
            if (attempt.firstFailure.plus(LOCK_DURATION).isBefore(Instant.now())
                    && (attempt.lockedUntil == null || attempt.lockedUntil.isBefore(Instant.now()))) {
                attempt.failures = 0;
                attempt.firstFailure = Instant.now();
                attempt.lockedUntil = null;
            }
            attempt.failures++;
            if (attempt.failures >= MAX_ATTEMPTS) {
                attempt.lockedUntil = Instant.now().plus(LOCK_DURATION);
            }
        }
        pruneExpired();
    }

    public void loginSucceeded(String key) {
        attempts.remove(key);
    }

    public boolean isBlocked(String key) {
        Attempt attempt = attempts.get(key);
        if (attempt == null || attempt.lockedUntil == null) {
            return false;
        }
        if (attempt.lockedUntil.isBefore(Instant.now())) {
            attempts.remove(key);
            return false;
        }
        return true;
    }

    /** Minutes until the lock for this key expires (0 when not locked). */
    public long minutesRemaining(String key) {
        Attempt attempt = attempts.get(key);
        if (attempt == null || attempt.lockedUntil == null) {
            return 0;
        }
        long seconds = Duration.between(Instant.now(), attempt.lockedUntil).getSeconds();
        return seconds <= 0 ? 0 : (seconds + 59) / 60;
    }

    /** Opportunistic cleanup so abandoned keys do not accumulate forever. */
    private void pruneExpired() {
        if (attempts.size() < 1000) {
            return;
        }
        Instant cutoff = Instant.now().minus(LOCK_DURATION);
        attempts.entrySet().removeIf(e -> {
            Attempt a = e.getValue();
            boolean lockExpired = a.lockedUntil != null && a.lockedUntil.isBefore(Instant.now());
            boolean stale = a.lockedUntil == null && a.firstFailure.isBefore(cutoff);
            return lockExpired || stale;
        });
    }
}
