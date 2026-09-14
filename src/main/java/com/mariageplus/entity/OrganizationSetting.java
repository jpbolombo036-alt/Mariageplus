package com.mariageplus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Réglages spécifiques d'une organisation, pilotés par le SUPER_ADMIN.
 * Valeur NULL = hériter du réglage global de la plateforme (app_settings).
 * Entité autonome sans BaseEntity : la clé primaire est l'identifiant de
 * l'organisation (relation 1-1), même logique qu'AppSetting pour sa clé.
 */
@Entity
@Table(name = "organization_settings")
@Getter
@Setter
public class OrganizationSetting {

    @Id
    @Column(name = "organization_id")
    private Long organizationId;

    /** NULL = hériter du global (app_settings.whatsapp_sending_enabled). */
    @Column(name = "whatsapp_enabled")
    private Boolean whatsappEnabled;

    /** NULL = hériter du global (app_settings.event_creation_enabled). */
    @Column(name = "event_creation_enabled")
    private Boolean eventCreationEnabled;

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
