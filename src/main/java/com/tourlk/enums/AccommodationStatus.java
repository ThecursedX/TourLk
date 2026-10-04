package com.tourlk.enums;

/**
 * Lifecycle status of an accommodation (hotel/property) listing.
 * <p>
 * FULLY_BOOKED is set automatically when no room type has availability
 * for today; TEMPORARILY_UNAVAILABLE is set by the hotel partner. Both
 * stay publicly visible, but TEMPORARILY_UNAVAILABLE is not bookable.
 */
public enum AccommodationStatus {
    DRAFT,
    PENDING_APPROVAL,
    ACTIVE,
    FULLY_BOOKED,
    TEMPORARILY_UNAVAILABLE,
    INACTIVE,
    ARCHIVED
}
