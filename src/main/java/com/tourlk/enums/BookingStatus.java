package com.tourlk.enums;

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
    CANCELLED
}
