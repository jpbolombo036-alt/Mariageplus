package com.mariageplus.dto.venue;

import com.mariageplus.entity.VenueCurrency;
import com.mariageplus.entity.VenueExpenseCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Dépenses d'une période : lignes, total par devise et répartition par poste. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueExpenseListResponse {
    private LocalDate from;
    private LocalDate to;
    /** false : seules les dépenses saisies par l'utilisateur connecté (caissier). */
    private boolean allRecorders;
    private List<VenueExpenseResponse> items;
    private List<CurrencyAmount> total;
    private List<CategoryTotal> byCategory;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryTotal {
        private VenueExpenseCategory category;
        private VenueCurrency currency;
        private BigDecimal amount;
        private long count;
    }
}
