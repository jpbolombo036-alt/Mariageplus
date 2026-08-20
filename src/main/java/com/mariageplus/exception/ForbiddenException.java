package com.mariageplus.exception;

/**
 * Accès autorisé mais refusé (isolation organisation / permission) → 403.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}