package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Réponse RSVP d'une invitation.
 *
 * Une seule réponse courante par invitation (unicité {@code invitation_id}),
 * ce qui garantit l'idempotence : une nouvelle soumission met à jour la réponse
 * existante (ACCEPTED → DECLINED et inversement) au lieu d'en créer plusieurs.
 * Le RSVP appartient à l'invitation, donc indirectement au guest et au mariage
 * de l'invitation (jamais à un autre).
 */
@Entity
@Table(name = "rsvps", uniqueConstraints = {
        @UniqueConstraint(name = "uk_rsvps_invitation", columnNames = {"invitation_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rsvp extends BaseEntity {

    @Column(name = "invitation_id", nullable = false)
    private Long invitationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RsvpStatus status;

    @Column(name = "number_of_attendees")
    private Integer numberOfAttendees;

    @Column(name = "drink_choice", length = 100)
    private String drinkChoice;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;
}
