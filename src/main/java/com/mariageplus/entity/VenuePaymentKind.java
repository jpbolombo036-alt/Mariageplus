package com.mariageplus.entity;

/** Sens d'un mouvement d'argent sur une réservation de salle. */
public enum VenuePaymentKind {
    /** Encaissement reçu du client. */
    PAYMENT,
    /** Remboursement rendu au client (après annulation). */
    REFUND
}
