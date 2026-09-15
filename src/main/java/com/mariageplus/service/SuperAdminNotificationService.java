package com.mariageplus.service;

import com.mariageplus.entity.User;
import com.mariageplus.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Notifications destinées aux SUPER_ADMIN.
 *
 * Cas principal : une nouvelle organisation vient de s'inscrire — ses
 * fonctions sont verrouillées par défaut (WhatsApp + création d'événements),
 * le SUPER_ADMIN doit les activer dans la console. L'envoi est asynchrone et
 * totalement non bloquant : un SMTP absent ou défaillant n'affecte jamais
 * l'inscription du nouveau compte.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SuperAdminNotificationService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final UserRepository userRepository;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Value("${spring.mail.from:${spring.mail.username:noreply@mariageplus.local}}")
    private String from;

    @Value("${app.frontend.url:}")
    private String frontendUrl;

    @Async
    public void notifyNewOrganization(Long organizationId, String organizationName,
                                      String creatorName, String creatorEmail) {
        try {
            if (!isConfigured()) {
                log.info("SMTP non configuré : notification SUPER_ADMIN ignorée (nouvelle org {}, {})",
                        organizationId, organizationName);
                return;
            }
            JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
            if (mailSender == null) {
                return;
            }

            List<User> superAdmins = userRepository.findActiveByRoleCode("SUPER_ADMIN");
            if (superAdmins.isEmpty()) {
                log.warn("Aucun SUPER_ADMIN actif : notification non envoyée (nouvelle org {})", organizationId);
                return;
            }

            String link = buildConsoleLink();
            String subject = "Nouvelle organisation à activer : " + organizationName;
            String body = "Nouvelle inscription sur la plateforme.\n\n"
                    + "Organisation : " + organizationName + "\n"
                    + "Compte créé par : " + creatorName + " (" + creatorEmail + ")\n\n"
                    + "Par défaut, cette organisation ne peut NI créer d'événements NI envoyer de WhatsApp.\n\n"
                    + "Pour l'activer :\n"
                    + "Console d'administration → Organisations → " + organizationName + "\n"
                    + "→ Réglages de l'organisation → activer les deux interrupteurs."
                    + (link.isEmpty() ? "" : "\n\nLien direct : " + link);

            int sent = 0;
            for (User admin : superAdmins) {
                try {
                    MimeMessage message = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
                    helper.setFrom(from);
                    helper.setTo(admin.getEmail());
                    helper.setSubject(subject);
                    helper.setText(body);
                    mailSender.send(message);
                    sent++;
                } catch (Exception ex) {
                    log.warn("Notification SUPER_ADMIN non envoyée à {} : {}", admin.getEmail(), ex.getMessage());
                }
            }
            log.info("Nouvelle organisation {} : notification envoyée à {} SUPER_ADMIN(s)", organizationId, sent);
        } catch (Exception ex) {
            log.warn("Notification de nouvelle organisation non envoyée : {}", ex.getMessage());
        }
    }

    private boolean isConfigured() {
        return StringUtils.hasText(smtpUsername) && StringUtils.hasText(smtpPassword);
    }

    private String buildConsoleLink() {
        String base = frontendUrl == null ? "" : frontendUrl.trim();
        if (!StringUtils.hasText(base)) {
            return "";
        }
        return base.endsWith("/") ? base + "dashboard/admin/organizations" : base + "/dashboard/admin/organizations";
    }
}
