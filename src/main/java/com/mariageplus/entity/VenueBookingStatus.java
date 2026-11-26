package com.mariageplus.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Cycle de vie d'une réservation de salle.
 * <pre>
 *   DEMANDE   → OPTION, CONFIRMEE, ANNULEE   (réservé à la future page publique)
 *   OPTION    → CONFIRMEE, ANNULEE           (date bloquée temporairement)
 *   CONFIRMEE → REALISEE, ANNULEE
 *   REALISEE, ANNULEE : terminaux
 * </pre>
 */
public enum VenueBookingStatus {
    DEMANDE,
    OPTION,
    CONFIRMEE,
    REALISEE,
    ANNULEE;

    /** Statuts qui occupent la salle : une autre réservation ne peut pas les chevaucher. */
    public static final Set<VenueBookingStatus> BLOCKING = EnumSet.of(OPTION, CONFIRMEE, REALISEE);

    public boolean isBlocking() {
        return BLOCKING.contains(this);
    }

    public boolean canTransitionTo(VenueBookingStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case DEMANDE -> target == OPTION || target == CONFIRMEE || target == ANNULEE;
            case OPTION -> target == CONFIRMEE || target == ANNULEE;
            case CONFIRMEE -> target == REALISEE || target == ANNULEE;
            case REALISEE, ANNULEE -> false;
        };
    }
}
