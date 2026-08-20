package com.mariageplus.service;

import com.mariageplus.entity.Guest;
import com.mariageplus.entity.Wedding;
import com.mariageplus.exception.MailDeliveryException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;

/**
 * Envoi optionnel de l'email d'invitation. Sans identifiants SMTP, aucun mail
 * n'est tenté : l'organisateur partage le lien manuellement.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationMailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final SpringTemplateEngine templateEngine;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Value("${spring.mail.from:noreply@mariageplus.local}")
    private String mailFrom;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public boolean isConfigured() {
        return StringUtils.hasText(smtpUsername) && StringUtils.hasText(smtpPassword);
    }

    public String publicInviteUrl(String publicToken) {
        String base = frontendUrl == null ? "" : frontendUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/invitations/" + publicToken;
    }

    /**
     * @return {@code true} si un email a réellement été remis au serveur SMTP
     */
    public boolean sendInvitation(Guest guest, Wedding wedding, String publicInviteUrl) {
        if (!isConfigured()) {
            log.warn("SMTP non configuré : invitation non envoyée par email");
            return false;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("JavaMailSender indisponible : invitation non envoyée par email");
            return false;
        }
        try {
            String couple = wedding.getDisplayName().trim();
            Context ctx = new Context(Locale.FRENCH);
            ctx.setVariable("guestFirstName", guest.getFirstName());
            ctx.setVariable("weddingDisplayName", couple);
            ctx.setVariable("publicInviteUrl", publicInviteUrl);
            String html = templateEngine.process("mail/invitation", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(guest.getEmail());
            helper.setSubject("Invitation — " + couple);
            helper.setText(html, true);
            mailSender.send(message);
            return true;
        } catch (MailDeliveryException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Échec d'envoi de l'invitation par email", ex);
            throw new MailDeliveryException("L'envoi de l'email a échoué. Réessayez plus tard.");
        }
    }
}
