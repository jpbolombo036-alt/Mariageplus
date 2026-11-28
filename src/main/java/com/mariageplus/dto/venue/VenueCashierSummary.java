package com.mariageplus.dto.venue;

import com.mariageplus.entity.VenueCurrency;
import com.mariageplus.entity.VenuePaymentKind;
import com.mariageplus.entity.VenuePaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * « Ma caisse » : ce que l'utilisateur connecté a lui-même encaissé (et
 * remboursé), sans le chiffre d'affaires global de la salle.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueCashierSummary {

    private LocalDate from;
    private LocalDate to;

    /** Encaissé net aujourd'hui, par devise. */
    private List<CurrencyAmount> today;

    /** Encaissé net sur la période, par devise. */
    private List<CurrencyAmount> collected;

    private long paymentCount;
    private long refundCount;

    /** Dépenses payées par l'utilisateur sur la période, par devise. */
    private List<CurrencyAmount> spent;

    /** Dépenses payées aujourd'hui, par devise. */
    private List<CurrencyAmount> todaySpent;

    /** Solde de la période : encaissé net − dépensé, par devise. */
    private List<CurrencyAmount> balance;

    private long expenseCount;

    /** Dépenses de la période, les plus récentes d'abord. */
    private List<VenueExpenseResponse> expenses;

    /** Net par mode et par devise sur la période (encaissé − remboursé − dépensé) : espèces en caisse, Mobile Money… */
    private List<MethodTotal> byMethod;

    /** Mouvements de la période, les plus récents d'abord. */
    private List<CashLine> movements;

    /** Réservations des 7 prochains jours (option, confirmée). */
    private List<VenueBookingResponse> upcoming;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MethodTotal {
        private VenuePaymentMethod method;
        private VenueCurrency currency;
        private BigDecimal amount;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashLine {
        private Long paymentId;
        private String receiptNumber;
        private VenuePaymentKind kind;
        private Long bookingId;
        private String bookingReference;
        private String clientName;
        private String venueName;
        private BigDecimal amount;
        private VenueCurrency currency;
        private VenuePaymentMethod method;
        private String reference;
        private LocalDateTime paidAt;
    }
}
