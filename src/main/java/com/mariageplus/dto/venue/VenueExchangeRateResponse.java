package com.mariageplus.dto.venue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Taux du jour de la salle et son historique (les plus récents d'abord). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueExchangeRateResponse {

    /** null tant qu'aucun taux n'a été fixé. */
    private Rate current;

    private List<Rate> history;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Rate {
        private Long id;
        private BigDecimal cdfPerUsd;
        private LocalDateTime effectiveAt;
        private String setByName;
        private String note;
    }
}
