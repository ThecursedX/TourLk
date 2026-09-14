package com.tourlk.exception;

/**
 * Thrown when a tourist tries to leave a second review for a
 * Booking/RoomReservation/VehicleHire they've already reviewed.
 * Handled by {@link GlobalExceptionHandler} and mapped to HTTP 409.
 */
public class DuplicateReviewException extends RuntimeException {

    public DuplicateReviewException(String message) {
        super(message);
    }

}
