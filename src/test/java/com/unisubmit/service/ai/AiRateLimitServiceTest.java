package com.unisubmit.service.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The AI surfaces are rate-limited per (user, bucket). These pin the two properties the UI
 * relies on: a bucket refuses once its hourly cap is reached, and buckets/users don't bleed
 * into each other.
 */
class AiRateLimitServiceTest {

    @Test
    void draftFeedbackRefusesOnceTheHourlyCapIsReached() {
        AiRateLimitService limiter = new AiRateLimitService();
        int cap = AiRateLimitService.Bucket.DRAFT_FEEDBACK.perHour();

        for (int i = 1; i <= cap; i++) {
            assertTrue(limiter.tryConsume(1L, AiRateLimitService.Bucket.DRAFT_FEEDBACK),
                    "call " + i + " of " + cap + " should be allowed");
        }
        assertFalse(limiter.tryConsume(1L, AiRateLimitService.Bucket.DRAFT_FEEDBACK),
                "the call past the cap must be refused");
    }

    @Test
    void bucketsAndUsersAreIndependent() {
        AiRateLimitService limiter = new AiRateLimitService();
        int cap = AiRateLimitService.Bucket.RERUN.perHour();

        // Exhaust RERUN for user 1.
        for (int i = 0; i < cap; i++) {
            limiter.tryConsume(1L, AiRateLimitService.Bucket.RERUN);
        }
        assertFalse(limiter.tryConsume(1L, AiRateLimitService.Bucket.RERUN),
                "user 1's RERUN bucket is exhausted");

        // A different bucket for the same user is untouched...
        assertTrue(limiter.tryConsume(1L, AiRateLimitService.Bucket.DRAFT_FEEDBACK),
                "a different bucket must have its own budget");
        // ...and so is the same bucket for a different user.
        assertTrue(limiter.tryConsume(2L, AiRateLimitService.Bucket.RERUN),
                "a different user must have their own budget");
    }
}
