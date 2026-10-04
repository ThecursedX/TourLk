package com.tourlk.enums;

/**
 * Lifecycle status of a payment.
 */
public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    REFUNDED,
    /** A refund has been decided (e.g. by a cancellation policy) and is being sent to Stripe. */
    REFUND_PENDING,
    /** Never completed (still PENDING) when its booking/reservation was cancelled or rejected. */
    CANCELLED
}
