package com.mariageplus.dto.user;

/**
 * Mot de passe temporaire, renvoyé une seule fois au SUPER_ADMIN pour être transmis
 * à l'utilisateur. {@code whatsappNumber} : chiffres internationaux pour wa.me, ou
 * null si le téléphone est absent ou ne peut pas être normalisé.
 */
public record TemporaryPasswordResponse(
        String temporaryPassword,
        String firstName,
        String lastName,
        String email,
        String phone,
        String whatsappNumber
) {
}
