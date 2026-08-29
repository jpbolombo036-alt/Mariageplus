package com.mariageplus.entity;

/**
 * Type d'un événement (nouvelle racine métier unifiée).
 *
 * Le mariage devient un type d'événement parmi d'autres. Chaque type partage
 * le socle commun de {@link Event} ; seul {@code WEDDING} dispose d'une
 * fiche de détails dédiée ({@link WeddingDetails}).
 */
public enum EventType {
    WEDDING,
    COLLATION,
    ANNIVERSARY,
    BAPTISM,
    GRADUATION,
    OTHER
}
