package com.mariageplus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "event_galleries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Gallery extends BaseEntity {
    @Column(name = "event_id", nullable = false, unique = true)
    private Long eventId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;
}
