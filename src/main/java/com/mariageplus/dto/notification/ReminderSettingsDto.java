package com.mariageplus.dto.notification;

import com.mariageplus.entity.ReminderSettings;

/**
 * Réglages des rappels. Dans une requête ou dans {@code settings}, null = hériter ;
 * dans {@code inherited}, toutes les valeurs sont renseignées.
 */
public record ReminderSettingsDto(
        Boolean enabled,
        Boolean firstReminderEnabled,
        Integer firstReminderDays,
        Boolean eveEnabled,
        Boolean dayOfEnabled,
        Integer sendHour,
        String timeZone
) {
    public static ReminderSettingsDto from(ReminderSettings s) {
        if (s == null) return empty();
        return new ReminderSettingsDto(s.getEnabled(), s.getFirstReminderEnabled(), s.getFirstReminderDays(),
                s.getEveEnabled(), s.getDayOfEnabled(), s.getSendHour(), s.getTimeZone());
    }

    public static ReminderSettingsDto empty() {
        return new ReminderSettingsDto(null, null, null, null, null, null, null);
    }

    /** Valeurs de ce niveau, complétées par celles de {@code parent} là où elles héritent. */
    public ReminderSettingsDto over(ReminderSettingsDto parent) {
        return new ReminderSettingsDto(
                enabled != null ? enabled : parent.enabled,
                firstReminderEnabled != null ? firstReminderEnabled : parent.firstReminderEnabled,
                firstReminderDays != null ? firstReminderDays : parent.firstReminderDays,
                eveEnabled != null ? eveEnabled : parent.eveEnabled,
                dayOfEnabled != null ? dayOfEnabled : parent.dayOfEnabled,
                sendHour != null ? sendHour : parent.sendHour,
                timeZone != null ? timeZone : parent.timeZone);
    }

    /**
     * Réponse d'un écran de réglages : ce niveau, ce qu'il hérite du niveau
     * supérieur, et si le fuseau horaire s'y règle (global et organisation).
     */
    public record View(String scope, Long scopeId, ReminderSettingsDto settings, ReminderSettingsDto inherited,
                       boolean timeZoneEditable) {
    }
}
