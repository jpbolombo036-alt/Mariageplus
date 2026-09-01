package com.mariageplus.service;

import com.mariageplus.entity.Invitation;
import com.mariageplus.repository.InvitationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;

/**
 * Carte d'invitation confirmée : image PNG générée côté navigateur par l'invité
 * après un RSVP ACCEPTED, puis enregistrée ici pour consultation ultérieure par
 * l'agent d'accueil.
 *
 * <p>Stockage : réutilise {@link StorageService} (S3-compatible) avec repli en
 * base, exactement comme les avatars et les photos d'événement. La référence
 * (clé S3 ou octets) est conservée sur l'invitation elle-même.</p>
 *
 * <p>Accès : endpoints publics par {@code publicToken} (l'invité est non
 * authentifié) ; la carte ne contient que des données déjà publiques (nom,
 * événement, nombre de participants, QR du publicToken).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationCardService {

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024; // 5 Mo
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private final InvitationService invitationService;
    private final InvitationRepository invitationRepository;
    private final StorageService storageService;

    /** Enregistre (ou remplace) la carte PNG de l'invitation. */
    @Transactional
    public void uploadCard(String publicToken, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier de la carte est requis");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("La carte dépasse la taille maximale (5 Mo)");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Fichier illisible");
        }
        if (!isPng(bytes)) {
            throw new IllegalArgumentException("Format invalide : la carte doit être une image PNG");
        }

        Invitation invitation = invitationService.resolvePublicInvitation(publicToken);
        String oldKey = invitation.getCardKey();
        if (storageService.isEnabled()) {
            String key = "invitation-cards/" + invitation.getWeddingId() + "/" + invitation.getId()
                    + "/" + Instant.now().toEpochMilli() + ".png";
            storageService.upload(key, bytes, "image/png");
            invitation.setCardKey(key);
            invitation.setCardImage(null);
            if (oldKey != null && !oldKey.equals(key)) {
                storageService.delete(oldKey);
            }
        } else {
            invitation.setCardKey(null);
            invitation.setCardImage(bytes);
        }
        invitationRepository.save(invitation);
        log.info("Carte d'invitation enregistrée : invitationId={}, s3={}", invitation.getId(),
                storageService.isEnabled());
    }

    /** Télécharge la carte ; null si aucune carte enregistrée. */
    @Transactional(readOnly = true)
    public CardImage downloadCard(String publicToken) {
        Invitation invitation = invitationService.resolvePublicInvitation(publicToken);
        if (invitation.getCardKey() != null && storageService.isEnabled()) {
            byte[] bytes = storageService.download(invitation.getCardKey());
            if (bytes != null) {
                return new CardImage(bytes, "image/png");
            }
        }
        if (invitation.getCardImage() != null && invitation.getCardImage().length > 0) {
            return new CardImage(invitation.getCardImage(), "image/png");
        }
        return null;
    }

    /** true si une carte est enregistrée pour cette invitation. */
    @Transactional(readOnly = true)
    public boolean hasCard(String publicToken) {
        Invitation invitation = invitationService.resolvePublicInvitation(publicToken);
        return hasCard(invitation);
    }

    private boolean hasCard(Invitation invitation) {
        return (invitation.getCardKey() != null && storageService.isEnabled())
                || (invitation.getCardImage() != null && invitation.getCardImage().length > 0);
    }

    private boolean isPng(byte[] bytes) {
        if (bytes == null || bytes.length < PNG_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (bytes[i] != PNG_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /** Image + type MIME. */
    public record CardImage(byte[] bytes, String contentType) {
    }
}