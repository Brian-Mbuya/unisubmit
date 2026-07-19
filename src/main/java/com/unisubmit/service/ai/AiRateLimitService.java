package com.unisubmit.service.ai;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window, per-(user, bucket) hourly rate limiter for the AI surfaces. Salvaged from
 * the retired AssistantService (which keyed by submission id) and re-keyed to (userId, bucket)
 * so each kind of AI action has its own budget.
 * <p>
 * In-memory: the window resets on redeploy — acceptable single-node (matches the login-attempt
 * and session model). Would need a shared store to scale horizontally.
 */
@Service
public class AiRateLimitService {

    /** The rate-limited AI actions and their per-hour caps. */
    public enum Bucket {
        DRAFT_TITLES(10),
        RERUN(4),
        DRAFT_FEEDBACK(15);

        private final int perHour;

        Bucket(int perHour) {
            this.perHour = perHour;
        }

        public int perHour() {
            return perHour;
        }
    }

    /** "(userId):(bucket)" → timestamps of calls inside the sliding hour window. */
    private final Map<String, Deque<Instant>> callLog = new ConcurrentHashMap<>();

    /**
     * Registers one call for (userId, bucket); returns false when that bucket's hourly cap
     * is already reached (in which case NO call is recorded).
     */
    public boolean tryConsume(Long userId, Bucket bucket) {
        String key = userId + ":" + bucket.name();
        Instant cutoff = Instant.now().minusSeconds(3600);
        Deque<Instant> calls = callLog.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (calls) {
            while (!calls.isEmpty() && calls.peekFirst().isBefore(cutoff)) {
                calls.pollFirst();
            }
            if (calls.size() >= bucket.perHour()) {
                return false;
            }
            calls.addLast(Instant.now());
            return true;
        }
    }
}
