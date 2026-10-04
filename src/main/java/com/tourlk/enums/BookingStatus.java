package com.tourlk.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle status of a booking.
 * <p>
 * RESCHEDULED is functionally equivalent to CONFIRMED (it still occupies
 * capacity on its current travelDate and can be completed or cancelled
 * like a CONFIRMED booking) — it exists as a separate value purely so the
 * booking history shows that its date changed at least once; see
 * {@code Booking#previousTravelDate}.
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    RESCHEDULE_REQUESTED,
    RESCHEDULED,
    COMPLETED,
    CANCELLED,
    /** A PENDING booking turned down by the package owner (GUIDE) or an ADMIN, with a reason. */
    REJECTED;

    /** Statuses that currently occupy a seat on their travelDate. RESCHEDULED counts — see above. */
    public static final Set<BookingStatus> CAPACITY_HOLDING = EnumSet.of(CONFIRMED, RESCHEDULED);

    /** Statuses a booking can never leave. Every other status is "active". */
    public static final Set<BookingStatus> TERMINAL = EnumSet.of(COMPLETED, CANCELLED, REJECTED);
}
