package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Rappel d'événement déjà envoyé (unique par événement, type et date). */
@Entity
@Table(name = "event_reminder_log",
        uniqueConstraints = @UniqueConstraint(name = "uq_event_reminder", columnNames = {"event_id", "kind", "event_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    /** J7 (entre J-7 et J-2), J1 (veille) ou J0 (jour J). */
    @Column(nullable = false, length = 10)
    private String kind;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}
