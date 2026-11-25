package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Réglages des rappels d'événements à un niveau donné. Chaque champ null hérite du
 * niveau supérieur : événement → organisation → global → valeurs du serveur.
 */
@Entity
@Table(name = "reminder_settings",
        uniqueConstraints = @UniqueConstraint(name = "uq_reminder_settings_scope", columnNames = {"scope", "scope_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReminderSettings {

    public enum Scope { GLOBAL, ORGANIZATION, EVENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Scope scope;

    /** 0 pour GLOBAL, sinon l'id de l'organisation ou de l'événement. */
    @Column(name = "scope_id", nullable = false)
    private Long scopeId;

    private Boolean enabled;

    @Column(name = "first_reminder_enabled")
    private Boolean firstReminderEnabled;

    @Column(name = "first_reminder_days")
    private Integer firstReminderDays;

    @Column(name = "eve_enabled")
    private Boolean eveEnabled;

    @Column(name = "day_of_enabled")
    private Boolean dayOfEnabled;

    @Column(name = "send_hour")
    private Integer sendHour;

    @Column(name = "time_zone", length = 64)
    private String timeZone;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
