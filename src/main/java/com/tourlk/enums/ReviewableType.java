package com.tourlk.enums;

/**
 * What a {@code Review} is reviewing. Mirrors {@link PayableType} — one
 * Review entity covering multiple reviewable things via a type + id
 * discriminator rather than several near-duplicate Review classes.
 */
public enum ReviewableType {
    TOUR_PACKAGE,
    ACCOMMODATION,
    VEHICLE
}
