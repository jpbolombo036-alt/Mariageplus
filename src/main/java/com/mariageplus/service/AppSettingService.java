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
        AppSetting setting = repository.findBySettingKey(KEY_WHATSAPP_SENDING_ENABLED)
                .orElseGet(() -> {
                    AppSetting s = new AppSetting();
                    s.setSettingKey(KEY_WHATSAPP_SENDING_ENABLED);
                    return s;
                });
        setting.setSettingValue(Boolean.toString(enabled));
        repository.save(setting);
        log.info("Réglage plateforme : {} = {}", KEY_WHATSAPP_SENDING_ENABLED, enabled);
        return enabled;
    }
}