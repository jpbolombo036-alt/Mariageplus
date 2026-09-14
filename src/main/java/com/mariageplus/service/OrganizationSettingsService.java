package com.mariageplus.service;

import com.mariageplus.entity.OrganizationSetting;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.repository.OrganizationRepository;
import com.mariageplus.repository.OrganizationSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Réglages par organisation (pilotés par le SUPER_ADMIN) avec héritage du
 * réglage global de la plateforme : une valeur explicite (true/false) dans
 * organization_settings prime ; sinon on retombe sur app_settings.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrganizationSettingsService {

    private final OrganizationSettingRepository repository;
    private final OrganizationRepository organizationRepository;
    private final AppSettingService appSettingService;

    /** La création d'événements est-elle permise pour cette organisation ? */
    @Transactional(readOnly = true)
    public boolean isEventCreationAllowed(Long organizationId) {
        Boolean override = getOverride(organizationId, OrganizationSetting::getEventCreationEnabled);
        return override != null ? override : appSettingService.isEventCreationEnabled();
    }

    /** L'envoi WhatsApp est-il permis pour cette organisation ? */
    @Transactional(readOnly = true)
    public boolean isWhatsappAllowed(Long organizationId) {
        Boolean override = getOverride(organizationId, OrganizationSetting::getWhatsappEnabled);
        return override != null ? override : appSettingService.isWhatsappSendingEnabled();
    }

    /** Ligne de réglages brute d'une organisation (null si jamais configurée). */
    @Transactional(readOnly = true)
    public OrganizationSetting getOrEmpty(Long organizationId) {
        return organizationId == null ? null : repository.findById(organizationId).orElse(null);
    }

    /** Définit (ou retire avec null) l'override « création d'événements » d'une organisation. */
    @Transactional
    public void setEventCreationEnabled(Long organizationId, Boolean enabled) {
        upsert(organizationId, s -> s.setEventCreationEnabled(enabled));
        log.info("Réglage organisation {} : event_creation_enabled = {}", organizationId, enabled);
    }

    /** Définit (ou retire avec null) l'override « envoi WhatsApp » d'une organisation. */
    @Transactional
    public void setWhatsappEnabled(Long organizationId, Boolean enabled) {
        upsert(organizationId, s -> s.setWhatsappEnabled(enabled));
        log.info("Réglage organisation {} : whatsapp_enabled = {}", organizationId, enabled);
    }

    private Boolean getOverride(Long organizationId, Function<OrganizationSetting, Boolean> getter) {
        return organizationId == null ? null
                : repository.findById(organizationId).map(getter).orElse(null);
    }

    private void upsert(Long organizationId, Consumer<OrganizationSetting> mutator) {
        if (organizationId == null) {
            throw new IllegalArgumentException("organizationId requis");
        }
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organisation introuvable : " + organizationId);
        }
        OrganizationSetting setting = repository.findById(organizationId).orElseGet(() -> {
            OrganizationSetting created = new OrganizationSetting();
            created.setOrganizationId(organizationId);
            return created;
        });
        mutator.accept(setting);
        repository.save(setting);
    }
}
