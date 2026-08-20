package com.mariageplus.entity;

/**
 * Statut d'une réponse RSVP.
 * Les soumissions publiques n'acceptent que ACCEPTED / DECLINED ; PENDING est
 * réservé à un état initial non répondu.
 */
public enum RsvpStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}
