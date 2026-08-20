package com.mariageplus.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window rate limiter for the public invitation endpoints. It limits both
 * a public token and a client IP so that a leaked token or a single abusive
 * client cannot exhaust the endpoint for all guests.
 */
@Component
public class PublicRsvpRateLimiter {

    private final int readPerToken;
    private final int submitPerToken;
    private final int perIp;
    private final long windowSeconds;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public PublicRsvpRateLimiter(
            @Value("${app.rate-limit.rsvp.read-per-token:30}") int readPerToken,
            @Value("${app.rate-limit.rsvp.submit-per-token:6}") int submitPerToken,
            @Value("${app.rate-limit.rsvp.per-ip:120}") int perIp,
            @Value("${app.rate-limit.rsvp.window-seconds:60}") long windowSeconds) {
        this.readPerToken = readPerToken;
        this.submitPerToken = submitPerToken;
        this.perIp = perIp;
        this.windowSeconds = windowSeconds;
    }

    public boolean tryAcquire(String clientIp, String publicToken, boolean submission) {
        int tokenLimit = submission ? submitPerToken : readPerToken;
        Instant now = Instant.now();
        String endpointType = submission ? "submit" : "read";
        return tryAcquire("token:" + endpointType + ":" + publicToken, tokenLimit, now)
                && tryAcquire("ip:" + clientIp, perIp, now);
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    private boolean tryAcquire(String key, int limit, Instant now) {
        if (limit < 1 || windowSeconds < 1) {
            throw new IllegalStateException("Les limites RSVP doivent etre strictement positives");
        }
        final boolean[] accepted = {false};
        counters.compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.windowEndsAt())) {
                accepted[0] = true;
                return new WindowCounter(1, now.plusSeconds(windowSeconds));
            }
            if (current.count() >= limit) {
                return current;
            }
            accepted[0] = true;
            return new WindowCounter(current.count() + 1, current.windowEndsAt());
        });
        return accepted[0];
    }

    private record WindowCounter(int count, Instant windowEndsAt) { }
}
