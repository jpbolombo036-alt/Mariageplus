package com.mariageplus.exception;

/**
 * Échec d'envoi SMTP alors que le mail est configuré → 502.
 * L'invitation ne doit pas passer à SENT.
 */
public class MailDeliveryException extends RuntimeException {
    public MailDeliveryException(String message) {
        super(message);
    }
}
