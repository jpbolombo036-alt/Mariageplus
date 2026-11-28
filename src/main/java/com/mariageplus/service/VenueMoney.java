package com.mariageplus.service;

import com.mariageplus.entity.VenueCurrency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Calculs monétaires du module « Salles ». Toujours en {@link BigDecimal},
 * arrondi au centime (HALF_UP). Le taux de change est exprimé en CDF pour 1 USD.
 */
public final class VenueMoney {

    private VenueMoney() {
    }

    public static BigDecimal scale(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
    }

    /** Différence par devise ({@code a} − {@code b}), sans conversion : USD et CDF restent séparés. */
    public static java.util.List<com.mariageplus.dto.venue.CurrencyAmount> subtract(
            java.util.List<com.mariageplus.dto.venue.CurrencyAmount> a,
            java.util.List<com.mariageplus.dto.venue.CurrencyAmount> b) {
        java.util.Map<VenueCurrency, BigDecimal> sums = new java.util.EnumMap<>(VenueCurrency.class);
        a.forEach(x -> sums.merge(x.getCurrency(), x.getAmount(), BigDecimal::add));
        b.forEach(x -> sums.merge(x.getCurrency(), x.getAmount().negate(), BigDecimal::add));
        return sums.entrySet().stream()
                .map(e -> com.mariageplus.dto.venue.CurrencyAmount.builder()
                        .currency(e.getKey()).amount(scale(e.getValue())).build())
                .toList();
    }

    /**
     * Convertit {@code amount} de la devise {@code from} vers {@code to}.
     *
     * @param cdfPerUsd taux (CDF pour 1 USD), obligatoire si les devises diffèrent
     * @throws IllegalArgumentException si le taux manque ou n'est pas positif
     */
    public static BigDecimal convert(BigDecimal amount, VenueCurrency from, VenueCurrency to, BigDecimal cdfPerUsd) {
        if (from == to) {
            return scale(amount);
        }
        if (cdfPerUsd == null || cdfPerUsd.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Le taux de change (CDF pour 1 USD) est obligatoire quand la devise du paiement diffère de celle de la réservation");
        }
        if (from == VenueCurrency.USD) {
            return scale(amount.multiply(cdfPerUsd));
        }
        return amount.divide(cdfPerUsd, 2, RoundingMode.HALF_UP);
    }

    /** Format lisible : « 1 250,00 USD ». */
    public static String format(BigDecimal amount, VenueCurrency currency) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        return format.format(scale(amount)) + " " + (currency == null ? "" : currency.name());
    }
}
