package com.mariageplus.entity;

/**
 * Statut d'un événement, avec les mêmes règles de transition que
 * {@link WeddingStatus} (cycle de vie identique repris tel quel).
 *
 * Transitions autorisées :
 * <pre>
 *   DRAFT      → PUBLISHED, CANCELLED
 *   PUBLISHED  → ACTIVE, CANCELLED
 *   ACTIVE     → COMPLETED, CANCELLED
 *   COMPLETED  → ARCHIVED
 *   ARCHIVED   → (terminal : aucun retour)
 *   CANCELLED  → (terminal : aucun retour)
 * </pre>
 */
public enum EventStatus {
    DRAFT,
    PUBLISHED,
    ACTIVE,
    COMPLETED,
    ARCHIVED,
    CANCELLED;

    /**
     * Retourne true si un passage vers le statut cible est autorisé depuis ce statut.
     */
    public boolean canTransitionTo(EventStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case DRAFT -> target == PUBLISHED || target == CANCELLED;
            case PUBLISHED -> target == ACTIVE || target == CANCELLED;
            case ACTIVE -> target == COMPLETED || target == CANCELLED;
            case COMPLETED -> target == ARCHIVED;
            case ARCHIVED, CANCELLED -> false;
        };
    }
}
