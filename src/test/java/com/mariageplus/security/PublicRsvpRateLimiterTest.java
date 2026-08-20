package com.mariageplus.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicRsvpRateLimiterTest {

    @Test
    void submission_ShouldRejectSeventhRequestForSameToken() {
        PublicRsvpRateLimiter limiter = new PublicRsvpRateLimiter(30, 6, 120, 60);

        for (int request = 0; request < 6; request++) {
            assertTrue(limiter.tryAcquire("192.0.2." + request, "public-token", true));
        }

        assertFalse(limiter.tryAcquire("192.0.2.99", "public-token", true));
    }

    @Test
    void read_ShouldRemainAvailableAfterSubmissionLimitIsReached() {
        PublicRsvpRateLimiter limiter = new PublicRsvpRateLimiter(2, 1, 120, 60);

        assertTrue(limiter.tryAcquire("192.0.2.1", "public-token", true));
        assertFalse(limiter.tryAcquire("192.0.2.2", "public-token", true));
        assertTrue(limiter.tryAcquire("192.0.2.3", "public-token", false));
    }

    @Test
    void request_ShouldRejectWhenIpLimitIsReached() {
        PublicRsvpRateLimiter limiter = new PublicRsvpRateLimiter(30, 6, 2, 60);

        assertTrue(limiter.tryAcquire("192.0.2.1", "token-a", false));
        assertTrue(limiter.tryAcquire("192.0.2.1", "token-b", false));
        assertFalse(limiter.tryAcquire("192.0.2.1", "token-c", false));
    }
}
