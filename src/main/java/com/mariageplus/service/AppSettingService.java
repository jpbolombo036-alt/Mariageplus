package com.mariageplus.service;

import com.mariageplus.entity.AppSetting;
import com.mariageplus.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Réglages plateforme (clé/valeur) : interrupteurs globaux pilotés par le
 * SUPER_ADMIN — ex. couper l'envoi WhatsApp en cas d'incident, de quota
 * atteint ou de suspension volontaire.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppSettingService {

    /** Coupe l'envoi WhatsApp sur toute la plateforme (boutons front + API). */
    public static final String KEY_WHATSAPP_SENDING_ENABLED = "whatsapp_sending_enabled";

    /** Plafond de relances WhatsApp par invitation (null = hériter du réglage env/global). */
    public static final String KEY_WHATSAPP_MAX_REMINDERS = "whatsapp_max_reminders";

    private final AppSettingRepository repository;

    @Transactional(readOnly = true)
    public boolean isWhatsappSendingEnabled() {
        return repository.findBySettingKey(KEY_WHATSAPP_SENDING_ENABLED)
                .map(s -> !"false".equalsIgnoreCase(
                        s.getSettingValue() == null ? "" : s.getSettingValue().trim()))
                .orElse(true);
    }

    @Transactional
    public boolean setWhatsappSendingEnabled(boolean enabled) {
        AppSetting setting = upsert(KEY_WHATSAPP_SENDING_ENABLED);
        setting.setSettingValue(Boolean.toString(enabled));
        repository.save(setting);
        log.info("Réglage plateforme : {} = {}", KEY_WHATSAPP_SENDING_ENABLED, enabled);
        return enabled;
    }

    /**
     * Plafond de relances WhatsApp défini en base (SUPER_ADMIN) : null si non
     * défini → l'appelant hérite de la variable d'environnement / du global.
     */
    @Transactional(readOnly = true)
    public Integer getWhatsappMaxReminders() {
        return repository.findBySettingKey(KEY_WHATSAPP_MAX_REMINDERS)
                .map(s -> {
                    try {
                        return Integer.parseInt(s.getSettingValue().trim());
                    } catch (NumberFormatException | NullPointerException ex) {
                        return null;
                    }
                })
                .filter(v -> v >= 0)
                .orElse(null);
    }

    /** Définit le plafond WhatsApp (null = réinitialiser → hériter du global). */
    @Transactional
    public Integer setWhatsappMaxReminders(Integer maxReminders) {
        if (maxReminders == null || maxReminders < 0) {
            repository.findBySettingKey(KEY_WHATSAPP_MAX_REMINDERS)
                    .ifPresent(repository::delete);
            log.info("Réglage plateforme : {} réinitialisé (héritage du global)",
                    KEY_WHATSAPP_MAX_REMINDERS);
            return null;
        }
        AppSetting setting = upsert(KEY_WHATSAPP_MAX_REMINDERS);
        setting.setSettingValue(Integer.toString(maxReminders));
        repository.save(setting);
        log.info("Réglage plateforme : {} = {}", KEY_WHATSAPP_MAX_REMINDERS, maxReminders);
        return maxReminders;
    }

    private AppSetting upsert(String key) {
        return repository.findBySettingKey(key).orElseGet(() -> {
            AppSetting s = new AppSetting();
            s.setSettingKey(key);
            return s;
        });
    }
}