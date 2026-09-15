package com.mariageplus.exception;

/**
 * Rejet par Meta pour dépassement de la limite de débit (HTTP 429 / codes
 * 130429, 131048). Le worker d'envoi en masse s'en sert pour marquer une
 * pause puis réessayer, au lieu de compter l'invitation en échec définitif.
 */
public class WhatsAppRateLimitException extends WhatsAppDeliveryException {

    public WhatsAppRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
