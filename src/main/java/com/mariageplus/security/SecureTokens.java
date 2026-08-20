package com.mariageplus.security;

import java.security.SecureRandom;

/**
 * Génération d'identifiants et de jetons aléatoires.
 *
 * Choix de sécurité : utilisation de {@link SecureRandom} (source aléatoire
 * cryptographiquement sûre) — jamais {@code Math.random()} ni les séquences
 * prévisibles ni un timestamp seul. Les valeurs sont indépendantes de l'id
 * numérique de l'entité.
 */
public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Alphabet des jetons publics (URL/identifiants non ambigus). */
    private static final char[] TOKEN_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    /** Alphabet du code d'invitation (sans caractères ambigus O/0/I/1). */
    private static final char[] CODE_ALPHABET =
            "ABCDEFGHIJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private SecureTokens() {
    }

    /** Jeton public aléatoire (ex : 32 caractères). */
    public static String randomToken(int length) {
        return random(TOKEN_ALPHABET, length);
    }

    /** Code d'invitation aléatoire (ex : 8 caractères). */
    public static String randomCode(int length) {
        return random(CODE_ALPHABET, length);
    }

    private static String random(char[] alphabet, int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("La longueur doit être positive");
        }
        char[] result = new char[length];
        for (int i = 0; i < length; i++) {
            result[i] = alphabet[RANDOM.nextInt(alphabet.length)];
        }
        return new String(result);
    }
}
