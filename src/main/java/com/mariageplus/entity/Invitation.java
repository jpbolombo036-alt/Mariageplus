package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Invitation d'un invité (guest) pour un mariage.
 *
 * - {@code invitationCode} : identifiant métier unique, non prévisible, généré
 *   côté backend.
 * - {@code publicToken} : jeton public aléatoire (accès invité), unique, séparé
 *   de l'id interne et du code ; jamais l'id/email/phone/nom.
 *
 * L'invité et le mariage doivent correspondre : guest.weddingId == weddingId.
 */
@Entity
@Table(name = "invitations", indexes = {
        @Index(name = "idx_invitations_wedding", columnList = "wedding_id"),
        @Index(name = "idx_invitations_guest", columnList = "guest_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invitation extends BaseEntity {

    @Column(name = "wedding_id", nullable = false)
    private Long weddingId;

    @Column(name = "guest_id", nullable = false)
    private Long guestId;

    @Column(name = "invitation_code", nullable = false, unique = true, length = 30)
    private String invitationCode;

    @Column(name = "public_token", nullable = false, unique = true, length = 64)
    private String publicToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.GENERATED;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "last_sent_at")
    private LocalDateTime lastSentAt;

    /** Nombre de relances déjà effectuées (limite max contrôlée côté service). */
    @Column(name = "reminder_count", nullable = false)
    @Builder.Default
    private int reminderCount = 0;

    /** Première ouverture du lien public (suivi). Null tant que l'invité n'a pas ouvert. */
    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    /** Carte d'invitation confirmée (PNG généré côté invité) : clé objet S3 (prioritaire) / fallback en base. */
    @Column(name = "card_key", length = 500)
    private String cardKey;

    @Column(name = "card_image")
    private byte[] cardImage;
}
