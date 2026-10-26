package com.mariageplus.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite les routes d'authentification par adresse réseau.
 * Le verrouillage par compte reste complémentaire : ce garde-fou empêche
 * surtout une attaque distribuée sur de nombreux comptes depuis une même IP.
 */
@Component
public class AuthRateLimiter {

    private final int loginPerIp;
    private final int registerPerIp;
    private final int refreshPerIp;
    private final long windowSeconds;
    private final int maxCounters = 100_000;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public AuthRateLimiter(
            @Value("${app.rate-limit.auth.login-per-ip:20}") int loginPerIp,
            @Value("${app.rate-limit.auth.register-per-ip:5}") int registerPerIp,
            @Value("${app.rate-limit.auth.refresh-per-ip:60}") int refreshPerIp,
            @Value("${app.rate-limit.auth.window-seconds:60}") long windowSeconds) {
        this.loginPerIp = loginPerIp;
        this.registerPerIp = registerPerIp;
        this.refreshPerIp = refreshPerIp;
        this.windowSeconds = windowSeconds;
    }

    public boolean tryAcquire(String clientIp, String endpoint) {
        int limit = switch (endpoint) {
            case "login" -> loginPerIp;
            case "register" -> registerPerIp;
            case "refresh" -> refreshPerIp;
            default -> throw new IllegalArgumentException("Route d'authentification inconnue");
        };
        Instant now = Instant.now();
        return tryAcquire("auth:" + endpoint + ":" + (clientIp == null ? "unknown" : clientIp), limit, now);
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    @Scheduled(fixedDelayString = "${app.rate-limit.auth.cleanup-ms:60000}")
    public void evictExpiredCounters() {
        Instant now = Instant.now();
        counters.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().windowEndsAt()));
    }

    private boolean tryAcquire(String key, int limit, Instant now) {
        if (limit < 1 || windowSeconds < 1) {
            throw new IllegalStateException("Les limites d'authentification doivent être strictement positives");
        }
        if (counters.size() >= maxCounters) {
            evictExpiredCounters();
            if (counters.size() >= maxCounters && !counters.containsKey(key)) {
                return false;
            }
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
