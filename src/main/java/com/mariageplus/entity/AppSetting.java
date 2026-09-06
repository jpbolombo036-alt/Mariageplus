package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Réglage global de la plateforme (clé/valeur), piloté par le SUPER_ADMIN.
 * Une ligne par réglage ; pas de soft-delete (une configuration se met à
 * jour, elle ne se supprime pas) — d'où l'entité autonome sans BaseEntity.
 */
@Entity
@Table(name = "app_settings")
@Getter
@Setter
public class AppSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, unique = true, length = 100)
    private String settingKey;

    @Column(name = "setting_value", length = 255)
    private String settingValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}