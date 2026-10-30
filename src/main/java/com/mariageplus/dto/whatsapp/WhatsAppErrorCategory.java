package com.mariageplus.dto.whatsapp;

/**
 * Catégories d'erreurs Meta Cloud API pour un message WhatsApp.
 *
 * <p>Chaque code est extrait du champ {@code error.code} de la réponse Meta
 * (ou du webhook) et permet de distinguer :
 * <ul>
 *   <li>les erreurs réversibles (rate limit, timeout) — réessai possible</li>
 *   <li>les erreurs de configuration (template absent, token expiré) — action admin</li>
 *   <li>les erreurs de destinataire (numéro invalide, compte suspendu) — exclusion</li>
 * </ul>
 *
 * La liste n'est pas exhaustive : les codes inconnus tombent dans
 * {@link #UNKNOWN} avec le code numérique original.
 */
public enum WhatsAppErrorCategory {

    // --- Erreurs réversibles (réessai possible) ---
    /** Limite de débit atteinte (HTTP 429). */
    RATE_LIMIT(131047, "Limite de débit atteinte"),
    /** Nombre de messages dépasse le quota journalier. */
    QUOTA_EXCEEDED(131048, "Quota journalier dépassé"),
    /** Timeout / indisponibilité de l'API Meta. */
    SERVICE_UNAVAILABLE(131001, "Indisponibilité temporaire de Meta"),
    /** Erreur interne Meta (5xx). */
    INTERNAL_ERROR(131000, "Erreur interne Meta"),

    // --- Erreurs de configuration (action admin requise) ---
    /** Template non approuvé ou inexistant. */
    TEMPLATE_NOT_APPROVED(131021, "Template non approuvé"),
    /** Token d'accès expiré ou révoqué. */
    TOKEN_EXPIRED(131025, "Token d'accès expiré"),
    /** Phone number ID invalide ou compte suspendu. */
    ACCOUNT_SUSPENDED(131026, "Compte suspendu"),
    /** Compte business non vérifié ou limité. */
    BUSINESS_RESTRICTED(131027, "Restriction commerciale"),
    /** Le numéro de téléphone n'est pas enregistré sur WhatsApp. */
    PHONE_NOT_REGISTERED(131030, "Numéro non enregistré sur WhatsApp"),

    // --- Erreurs de destinataire ---
    /** Numéro de téléphone invalide ou mal formaté. */
    INVALID_NUMBER(131031, "Numéro de téléphone invalide"),
    /** Compte destinataire suspendu ou supprimé. */
    RECIPIENT_UNAVAILABLE(131032, "Destinataire indisponible"),
    /** Le destinataire a bloqué l'expéditeur. */
    BLOCKED_BY_RECIPIENT(131033, "Bloqué par le destinataire"),

    // --- Autre ---
    /** Erreur non catégorisée : code inconnu ou message libre. */
    UNKNOWN(0, "Erreur inconnue");

    private final int code;
    private final String defaultLabel;

    WhatsAppErrorCategory(int code, String defaultLabel) {
        this.code = code;
        this.defaultLabel = defaultLabel;
    }

    /** Code numérique Meta (0 = inconnu). */
    public int code() { return code; }

    /** Libellé par défaut. */
    public String defaultLabel() { return defaultLabel; }

    /**
     * Trouve la catégorie correspondant à un code Meta.
     * Les codes inconnus retournent {@link #UNKNOWN} avec le code original.
     */
    public static WhatsAppErrorCategory fromCode(int code) {
        for (WhatsAppErrorCategory c : values()) {
            if (c.code == code) {
                return c;
            }
        }
        return UNKNOWN;
    }

    /** true si l'erreur est réversible (réessai possible). */
    public boolean isReversible() {
        return this == RATE_LIMIT || this == QUOTA_EXCEEDED
                || this == SERVICE_UNAVAILABLE || this == INTERNAL_ERROR;
    }

    /** true si l'erreur nécessite une action de l'administrateur. */
    public boolean requiresAdminAction() {
        return this == TEMPLATE_NOT_APPROVED || this == TOKEN_EXPIRED
                || this == ACCOUNT_SUSPENDED || this == BUSINESS_RESTRICTED;
    }

    /** true si le destinataire doit être exclu du prochain envoi. */
    public boolean excludesRecipient() {
        return this == INVALID_NUMBER || this == PHONE_NOT_REGISTERED
                || this == RECIPIENT_UNAVAILABLE || this == BLOCKED_BY_RECIPIENT;
    }
}