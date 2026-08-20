package com.mariageplus.exception;

/**
 * Conflit métier (ex : email déjà utilisé, code déjà existant) → 409.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}