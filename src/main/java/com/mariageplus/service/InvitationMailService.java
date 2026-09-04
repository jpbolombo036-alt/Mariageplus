package com.mariageplus.service;

import com.mariageplus.entity.Guest;
import com.mariageplus.entity.Event;
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

import java.time.format.DateTimeFormatter;
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
    private final IcsCalendarService icsCalendarService;

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter TIME_FR =
            DateTimeFormatter.ofPattern("HH'h'mm");

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Value("${spring.mail.from:noreply@mariageplus.local}")
    private String mailFrom;

    @Value("${app.frontend.url:}")
    private String frontendUrl;

    @Value("${app.public.url:}")
    private String publicBaseUrl;

    @jakarta.annotation.PostConstruct
    void warnIfLocalFrontendUrl() {
        if (StringUtils.hasText(frontendUrl) && frontendUrl.contains("localhost")) {
            log.warn("app.frontend.url pointe vers localhost ({}). Les liens d'invitation envoyés aux "
                    + "invités ne fonctionneront pas hors de cette machine. Définissez APP_PUBLIC_URL ou "
                    + "APP_FRONTEND_URL avec l'URL publique du front (ex. https://mariageplus-web.vercel.app) "
                    + "en production.", frontendUrl);
        }
        if (!StringUtils.hasText(publicBaseUrl) && !StringUtils.hasText(frontendUrl)) {
            log.warn("Aucune URL publique configurée (APP_PUBLIC_URL ou APP_FRONTEND_URL). "
                    + "Les liens d'invitation seront invalides.");
        }
    }

    public boolean isConfigured() {
        return StringUtils.hasText(smtpUsername) && StringUtils.hasText(smtpPassword);
    }

    public String publicInviteUrl(String publicToken) {
        String base = resolvePublicBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/invitations/" + publicToken;
    }

    private String resolvePublicBaseUrl() {
        if (StringUtils.hasText(publicBaseUrl)) {
            return publicBaseUrl.trim();
        }
        if (StringUtils.hasText(frontendUrl)) {
            return frontendUrl.trim();
        }
        return "";
    }

    /**
     * @return {@code true} si un email a réellement été remis au serveur SMTP
     */
    public boolean sendInvitation(Guest guest, Event event, String publicInviteUrl) {
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
            String couple = event.getName().trim();
            Context ctx = new Context(Locale.FRENCH);
            ctx.setVariable("guestFirstName", guest.getFirstName());
            ctx.setVariable("weddingDisplayName", couple);
            ctx.setVariable("publicInviteUrl", publicInviteUrl);
            ctx.setVariable("invitationMessage", event.getMessage());
            if (event != null && event.getEventDate() != null) {
                ctx.setVariable("eventName", event.getName());
                ctx.setVariable("eventDate", DATE_FR.format(event.getEventDate()));
                ctx.setVariable("eventStartTime",
                        event.getStartTime() == null ? null : TIME_FR.format(event.getStartTime()));
                ctx.setVariable("eventVenue", venueText(event));
            }
            String html = templateEngine.process("mail/invitation", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(guest.getEmail());
            helper.setSubject("Invitation — " + couple);
            helper.setText(html, true);

            // Pièce jointe calendrier (.ics) pour ajouter au calendrier d'un clic.
            String ics = icsCalendarService.buildIcs(event, null);
            if (ics != null) {
                helper.addAttachment("invitation.ics",
                        new jakarta.activation.DataSource() {
                            @Override
                            public java.io.InputStream getInputStream() {
                                return new java.io.ByteArrayInputStream(ics.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            }
                            @Override
                            public java.io.OutputStream getOutputStream() {
                                throw new UnsupportedOperationException();
                            }
                            @Override
                            public String getContentType() {
                                return "text/calendar";
                            }
                            @Override
                            public String getName() {
                                return "invitation.ics";
                            }
                        });
            }

            mailSender.send(message);
            return true;
        } catch (MailDeliveryException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Échec d'envoi de l'invitation par email", ex);
            throw new MailDeliveryException("L'envoi de l'email a échoué. Réessayez plus tard.");
        }
    }

    private String venueText(Event event) {
        StringBuilder sb = new StringBuilder();
        if (event.getVenueName() != null) sb.append(event.getVenueName());
        if (event.getVenueAddress() != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(event.getVenueAddress());
        }
        if (event.getCity() != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(event.getCity());
        }
        return sb.toString();
    }
}
