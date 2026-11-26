package com.mariageplus.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Paiement reçu pour une réservation, saisi par la salle. Il garde sa propre
 * devise ; si elle diffère de celle de la réservation, le taux du jour
 * ({@code exchangeRate} = CDF pour 1 USD) sert à calculer
 * {@code amountInBookingCurrency}, utilisé pour le reste à payer.
 */
@Entity
@Table(name = "venue_booking_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VenueBookingPayment extends BaseEntity {

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private VenueCurrency currency;

    @Column(name = "exchange_rate", precision = 14, scale = 4)
    private BigDecimal exchangeRate;

    @Column(name = "amount_in_booking_currency", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountInBookingCurrency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VenuePaymentMethod method;

    /** Référence de transaction (ex : numéro M-Pesa). */
    @Column(length = 100)
    private String reference;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(length = 500)
    private String note;

    @Column(name = "recorded_by")
    private Long recordedBy;
}
