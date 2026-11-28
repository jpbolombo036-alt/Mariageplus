package com.mariageplus.dto.venue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Encaissements d'une personne (caissier ou gérant), pour le contrôle de caisse du gérant. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueCashierTotal {

    private Long userId;
    private String name;

    /** Net encaissé aujourd'hui, par devise. */
    private List<CurrencyAmount> today;

    /** Net encaissé sur la période, par devise. */
    private List<CurrencyAmount> collected;

    /** Part en espèces (doit se retrouver dans la caisse), par devise. */
    private List<CurrencyAmount> cash;

    /** Part en Mobile Money (M-Pesa, Orange Money, Airtel Money), par devise. */
    private List<CurrencyAmount> mobileMoney;

    /** Part en virement bancaire, par devise. */
    private List<CurrencyAmount> transfer;

    private long paymentCount;
    private long refundCount;
    /** Dépenses payées par la personne sur la période, par devise. */
    private List<CurrencyAmount> spent;
    /** Espèces encaissées − dépenses payées en espèces : ce que la caisse doit contenir. */
    private List<CurrencyAmount> cashInHand;
}
