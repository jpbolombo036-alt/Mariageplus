package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Utilisateur de la plateforme.
 * Le mot de passe est stocké hashé (BCrypt) et n'est jamais exposé par l'API.
 * Un utilisateur peut posséder plusieurs rôles (via {@link UserRole}).
 */
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"email"}),
        @UniqueConstraint(columnNames = {"phone"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(name = "email_verified")
    private boolean emailVerified = false;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /** Number of consecutive failed login attempts. Reset after a successful login. */
    @Builder.Default
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    /** Temporary lock expiry after too many failed login attempts; null when unlocked. */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Builder.Default
    @Column(name = "token_version", nullable = false)
    private long tokenVersion = 0;

    /** Photo de profil (image stockée en base, max 2 Mo, validée côté service). */
    @Column(name = "avatar")
    private byte[] avatar;

    /** Clé de l'objet dans le stockage S3-compatible (prioritaire sur la colonne avatar). */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;
}
