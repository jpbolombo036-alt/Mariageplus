package com.mariageplus.exception;

import com.mariageplus.dto.whatsapp.WhatsAppErrorCategory;

/**
 * Échec d'envoi d'un message WhatsApp (API Cloud Meta injoignable, erreur
 * d'API, template refusé, numéro hors WhatsApp...) → 502, comme l'email.
 *
 * <p>Le champ {@link #errorCategory} (quand il est renseigné) permet au
 * contrôleur et au worker de prendre une décision :
 * <ul>
 *   <li>réessai immédiat pour les erreurs réversibles</li>
 *   <li>exclusion du destinataire pour les erreurs de numéro</li>
 *   <li>signal admin pour les erreurs de configuration</li>
 * </ul>
 */
public class WhatsAppDeliveryException extends RuntimeException {

    private final WhatsAppErrorCategory errorCategory;
    private final int errorCode;

    public WhatsAppDeliveryException(String message) {
        super(message);
        this.errorCategory = WhatsAppErrorCategory.UNKNOWN;
        this.errorCode = 0;
    }

    public WhatsAppDeliveryException(String message, Throwable cause) {
        super(message, cause);
        this.errorCategory = WhatsAppErrorCategory.UNKNOWN;
        this.errorCode = 0;
    }

    public WhatsAppDeliveryException(String message, WhatsAppErrorCategory category, int errorCode) {
        super(message);
        this.errorCategory = category;
        this.errorCode = errorCode;
    }

    public WhatsAppDeliveryException(String message, WhatsAppErrorCategory category, int errorCode, Throwable cause) {
        super(message, cause);
        this.errorCategory = category;
        this.errorCode = errorCode;
    }

    /** Catégorie de l'erreur Meta (toujours non-null). */
    public WhatsAppErrorCategory getErrorCategory() {
        return errorCategory;
    }

    /** Code numérique Meta (0 = inconnu). */
    public int getErrorCode() {
        return errorCode;
    }
}