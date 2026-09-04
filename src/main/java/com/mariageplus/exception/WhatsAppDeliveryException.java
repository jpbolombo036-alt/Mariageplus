package com.mariageplus.exception;

/**
 * Échec d'envoi d'un message WhatsApp (API Cloud Meta injoignable, erreur
 * d'API, template refusé, numéro hors WhatsApp...) → 502, comme l'email.
 */
public class WhatsAppDeliveryException extends RuntimeException {
    public WhatsAppDeliveryException(String message) {
        super(message);
    }

    public WhatsAppDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
