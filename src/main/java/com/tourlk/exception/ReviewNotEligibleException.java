package com.tourlk.exception;

/**
 * Thrown when a tourist tries to review something they haven't actually
 * completed — the source Booking/RoomReservation/VehicleHire either
 * doesn't belong to them or isn't COMPLETED yet.
 * Handled by {@link GlobalExceptionHandler} and mapped to HTTP 400.
 */
public class ReviewNotEligibleException extends RuntimeException {

    public ReviewNotEligibleException(String message) {
        super(message);
    }

}
