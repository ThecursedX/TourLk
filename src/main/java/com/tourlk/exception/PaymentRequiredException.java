package com.tourlk.exception;

/**
 * Thrown when starting a payment for a Booking/RoomReservation that
 * isn't actually awaiting payment (its status isn't PENDING — it may
 * already be confirmed, cancelled, or otherwise no longer payable).
 * Handled by {@link GlobalExceptionHandler} and mapped to HTTP 400.
 */
public class PaymentRequiredException extends RuntimeException {

    public PaymentRequiredException(String message) {
        super(message);
    }

}
