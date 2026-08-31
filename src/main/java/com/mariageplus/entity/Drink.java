package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "drinks", indexes = {
        @Index(name = "idx_drinks_wedding", columnList = "wedding_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Drink extends BaseEntity {

    @Column(name = "wedding_id", nullable = false)
    private Long weddingId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
