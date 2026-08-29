package com.mariageplus.entity;

/**
 * Type d'une session (sous-étape) d'un événement.
 * Reprend les valeurs de {@link WeddingEventType} — une collation ou un
 * anniversaire peuvent aussi avoir des sessions (vin d'honneur, soirée...).
 */
public enum EventSessionType {
    CIVIL_CEREMONY,
    RELIGIOUS_CEREMONY,
    RECEPTION,
    AFTER_PARTY,
    OTHER
}
