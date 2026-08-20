package com.mariageplus.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Fournisseur de tokens JWT (HS256). Le token embarque :
 *  - subject = id utilisateur
 *  - username (email)
 *  - roles    (ROLE_*)
 *  - permissions (codes granulaires)
 *  - organizationId (périmètre, null pour SUPER_ADMIN)
 * Le secret doit contenir au moins 256 bits (32 octets).
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    @Value("${app.jwt.refresh-expiration}")
    private long refreshExpiration;

    public long getExpirationSeconds() {
        return jwtExpiration / 1000;
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpiration / 1000;
    }

    @PostConstruct
    public void validateSecret() {
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException("JWT secret is not configured. Set app.jwt.secret or JWT_SECRET.");
        }
        String trimmed = jwtSecret.trim();
        if (trimmed.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret must be at least 256 bits (32 bytes).");
        }
        jwtSecret = trimmed;
    }

    public String generateToken(UserPrincipal principal) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .subject(Long.toString(principal.getId()))
                .claim("username", principal.getEmail())
                .claim("roles", principal.getRoles())
                .claim("permissions", principal.getPermissions())
                .claim("organizationId", principal.getOrganizationId() == null ? null : principal.getOrganizationId())
                .claim("tokenVersion", principal.getTokenVersion())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(Long userId, Long organizationId) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshExpiration);

        return Jwts.builder()
                .subject(Long.toString(userId))
                .claim("type", "refresh")
                .claim("organizationId", organizationId == null ? null : organizationId)
                .claim("jti", java.util.UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        return Long.valueOf(getClaims(token).getSubject());
    }

    public Long getTokenVersionFromToken(String token) {
        return getClaims(token).get("tokenVersion", Long.class);
    }

    public boolean isRefreshToken(String token) {
        try {
            return "refresh".equals(getClaims(token).get("type"));
        } catch (Exception ex) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        return getClaims(token).get("roles", List.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getPermissionsFromToken(String token) {
        return getClaims(token).get("permissions", List.class);
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (io.jsonwebtoken.JwtException ex) {
            // Couvre : malformé, expiré, non supporté, signature invalide (altéré).
            log.debug("JWT invalide : {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.debug("JWT vide ou mal formé");
        }
        return false;
    }

    private Claims getClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
