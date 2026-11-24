package com.mariageplus.exception;

/**
 * Action répétée trop vite par le même utilisateur → 429.
 */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
