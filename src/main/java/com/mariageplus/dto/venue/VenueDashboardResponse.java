package com.mariageplus.dto.venue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueDashboardResponse {

    private LocalDate from;
    private LocalDate to;
    private Long venueId;
    private int venueCount;

    /** Nombre de réservations par statut (débutant dans la période). */
    private Map<String, Long> bookingsByStatus;

    /** Encaissé pendant la période, par devise du paiement. */
    private List<CurrencyAmount> collected;

    /** Valeur des réservations confirmées / réalisées de la période, par devise. */
    private List<CurrencyAmount> bookedValue;

    /** Reste à payer sur toutes les réservations confirmées / réalisées, par devise. */
    private List<CurrencyAmount> outstanding;

    /** Jours-salle occupés (confirmées / réalisées) sur jours-salle disponibles, en %. */
    private double occupancyRate;
    private long occupiedVenueDays;
    private long availableVenueDays;

    /** Réservations avec un reste à payer (les plus proches d'abord, 50 max). */
    private List<VenueBookingResponse> unpaid;

    /** Réservations à venir sur 30 jours (option, confirmée). */
    private List<VenueBookingResponse> upcoming;
}
