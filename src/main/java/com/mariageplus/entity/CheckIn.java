package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Enregistrement réel d'entrée (check-in) d'une invitation le jour du mariage.
 *
 * Chaque ligne représente une opération de contrôle (arrivée partielle possible).
 * Le total réellement entré pour une invitation est calculé par
 * {@code SUM(number_of_attendees)}. Les champs weddingId/guestId ne sont pas
 * stockés : ils sont obtenus de façon fiable via l'invitation (isolation).
 * {@code checkedInBy} trace l'agent ayant réalisé le contrôle (audit).
 */
@Entity
@Table(name = "checkins", indexes = {
        @Index(name = "idx_checkins_invitation", columnList = "invitation_id"),
        @Index(name = "idx_checkins_checked_at", columnList = "checked_in_at"),
        @Index(name = "idx_checkins_device_id", columnList = "device_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckIn extends BaseEntity {

    @Column(name = "invitation_id", nullable = false)
    private Long invitationId;

    @Column(name = "number_of_attendees", nullable = false)
    private Integer numberOfAttendees;

    @Column(name = "checked_in_at", nullable = false)
    private LocalDateTime checkedInAt;

    @Column(name = "checked_in_by")
    private Long checkedInBy;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "is_offline")
    private Boolean offline;

    /**
     * Compteur monotone émis par l'appareil (jamais remis à zéro) : clé
     * d'idempotence de la synchronisation hors ligne, contrainte unique en base
     * pour empêcher tout doublon lors d'un rejeu de batch.
     */
    @Column(name = "sequence_number")
    private Long sequenceNumber;
}