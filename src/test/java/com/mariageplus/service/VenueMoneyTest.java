package com.mariageplus.service;

import com.mariageplus.entity.VenueBookingStatus;
import com.mariageplus.entity.VenueCurrency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VenueMoneyTest {

    @Test
    void sameCurrency_isOnlyRounded() {
        assertThat(VenueMoney.convert(new BigDecimal("10.005"), VenueCurrency.USD, VenueCurrency.USD, null))
                .isEqualByComparingTo("10.01");
    }

    @Test
    void cdfToUsd_dividesByRate() {
        assertThat(VenueMoney.convert(new BigDecimal("280000"), VenueCurrency.CDF, VenueCurrency.USD, new BigDecimal("2800")))
                .isEqualByComparingTo("100.00");
        assertThat(VenueMoney.convert(new BigDecimal("1000"), VenueCurrency.CDF, VenueCurrency.USD, new BigDecimal("2850")))
                .isEqualByComparingTo("0.35");
    }

    @Test
    void usdToCdf_multipliesByRate() {
        assertThat(VenueMoney.convert(new BigDecimal("150"), VenueCurrency.USD, VenueCurrency.CDF, new BigDecimal("2815.5")))
                .isEqualByComparingTo("422325.00");
    }

    @Test
    void differentCurrency_withoutRate_isRejected() {
        assertThatThrownBy(() -> VenueMoney.convert(BigDecimal.TEN, VenueCurrency.CDF, VenueCurrency.USD, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taux de change");
        assertThatThrownBy(() -> VenueMoney.convert(BigDecimal.TEN, VenueCurrency.CDF, VenueCurrency.USD, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void format_usesSpacesAndComma() {
        assertThat(VenueMoney.format(new BigDecimal("1250"), VenueCurrency.USD)).isEqualTo("1 250,00 USD");
        assertThat(VenueMoney.format(new BigDecimal("2800000.5"), VenueCurrency.CDF)).isEqualTo("2 800 000,50 CDF");
    }

    @Test
    void bookingStatus_transitions() {
        assertThat(VenueBookingStatus.OPTION.canTransitionTo(VenueBookingStatus.CONFIRMEE)).isTrue();
        assertThat(VenueBookingStatus.DEMANDE.canTransitionTo(VenueBookingStatus.CONFIRMEE)).isTrue();
        assertThat(VenueBookingStatus.CONFIRMEE.canTransitionTo(VenueBookingStatus.REALISEE)).isTrue();
        assertThat(VenueBookingStatus.OPTION.canTransitionTo(VenueBookingStatus.REALISEE)).isFalse();
        assertThat(VenueBookingStatus.ANNULEE.canTransitionTo(VenueBookingStatus.OPTION)).isFalse();
        assertThat(VenueBookingStatus.REALISEE.canTransitionTo(VenueBookingStatus.ANNULEE)).isFalse();
        assertThat(VenueBookingStatus.DEMANDE.isBlocking()).isFalse();
        assertThat(VenueBookingStatus.OPTION.isBlocking()).isTrue();
        assertThat(VenueBookingStatus.ANNULEE.isBlocking()).isFalse();
    }
}
