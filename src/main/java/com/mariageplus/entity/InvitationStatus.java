package com.mariageplus.entity;

/**
 * Statut d'une invitation. Aucune transition complexe à cette étape : la valeur
 * est posée à la création et peut être modifiée (ex : CANCELLED). La logique RSVP
 * n'est pas traitée ici.
 */
public enum InvitationStatus {
    DRAFT,
    GENERATED,
    SENT,
    CANCELLED,
    EXPIRED
}
