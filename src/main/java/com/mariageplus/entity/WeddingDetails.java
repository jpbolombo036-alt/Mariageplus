package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Détails spécifiques à un événement de type MARIAGE (relation 1-1 avec
 * {@link Event}). Reprend les champs de l'ancienne entité {@link Wedding}
 * qui sont propres au mariage (époux, épouse, photos, messages).
 *
 * Phase 1 — coexistence : cette table n'est peuplée que par les événements
 * créés via /api/events ; la migration Flyway (Phase 2) recopiera les mariages.
 */
@Entity
@Table(name = "wedding_details", indexes = {
        @Index(name = "idx_wedding_details_event", columnList = "event_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeddingDetails extends BaseEntity {

    @Column(name = "event_id", nullable = false, unique = true)
    private Long eventId;

    @Column(name = "groom_first_name", length = 100)
    private String groomFirstName;

    @Column(name = "groom_last_name", length = 100)
    private String groomLastName;

    @Column(name = "bride_first_name", length = 100)
    private String brideFirstName;

    @Column(name = "bride_last_name", length = 100)
    private String brideLastName;

    @Column(name = "groom_photo_url", length = 1000)
    private String groomPhotoUrl;

    @Column(name = "bride_photo_url", length = 1000)
    private String bridePhotoUrl;

    @Column(name = "couple_photo_url", length = 1000)
    private String couplePhotoUrl;

    @Column(name = "welcome_message", length = 2000)
    private String welcomeMessage;

    /** Nom d'affichage calculé : "GroomFirstName GroomLastName & BrideFirstName BrideLastName". */
    public String getDisplayName() {
        return (groomFirstName == null ? "" : groomFirstName)
                + " " + (groomLastName == null ? "" : groomLastName)
                + " & " + (brideFirstName == null ? "" : brideFirstName)
                + " " + (brideLastName == null ? "" : brideLastName);
    }
}
