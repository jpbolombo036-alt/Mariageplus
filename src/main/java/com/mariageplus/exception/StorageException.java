package com.mariageplus.exception;

/**
 * Échec du stockage objet S3-compatible : non configuré (S3_BUCKET /
 * S3_ACCESS_KEY manquants), bucket inexistant, identifiants révoqués ou
 * réseau indisponible → 502.
 *
 * <p>L'image n'a <b>pas</b> été enregistrée : l'appelant peut soit remonter
 * une erreur claire à l'utilisateur, soit retomber sur le stockage en base
 * (comportement utilisé quand le stockage objet est désactivé).</p>
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
