package com.mariageplus.util;

/**
 * Normalisation des numéros de téléphone vers l'E.164 (ex : +2250701020304),
 * format attendu par l'API WhatsApp Cloud (transmis sans le "+").
 *
 * Règles supportées :
 * - espaces, tirets, parenthèses et points retirés
 * - préfixe international "00" → "+"
 * - numéro local commençant par 0 → préfixé par l'indicatif pays par défaut
 *   (ex : 07 01 02 03 04 + 225 → +2250701020304)
 */
public final class PhoneNormalizer {

    private PhoneNormalizer() {
    }

    /**
     * @return le numéro au format E.164 ("+XXXXXXXX") ou null si invalide/absent.
     */
    public static String toE164(String raw, String defaultCountryCode) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.trim();
        boolean hasPlus = digits.startsWith("+");
        if (digits.startsWith("00")) {
            hasPlus = true;
            digits = digits.substring(2);
        } else if (hasPlus) {
            digits = digits.substring(1);
        }
        digits = digits.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (!hasPlus) {
            if (digits.startsWith("0")) {
                String cc = defaultCountryCode == null ? "" : defaultCountryCode.replaceAll("[^0-9]", "");
                if (cc.isEmpty()) {
                    return null; // numéro local sans indicatif pays connu
                }
                digits = cc + digits.substring(1);
            } else {
                // Déjà international sans "+" : on l'accepte tel quel.
            }
        }
        // E.164 : 8 à 15 chiffres après le "+".
        if (digits.length() < 8 || digits.length() > 15) {
            return null;
        }
        return "+" + digits;
    }

    /** Identifiant WhatsApp : E.164 sans le "+" (ex : 2250701020304). */
    public static String toWhatsAppId(String e164) {
        if (e164 == null) {
            return null;
        }
        String id = e164.startsWith("+") ? e164.substring(1) : e164;
        return id.matches("[0-9]{8,15}") ? id : null;
    }
}
