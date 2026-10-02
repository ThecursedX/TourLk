package com.tourlk.exception;

/**
 * Thrown when removing a package departure that still has active
 * (non-cancelled, non-completed) bookings on its date.
 * Handled by {@link GlobalExceptionHandler} and mapped to HTTP 409.
 */
public class DepartureHasBookingsException extends RuntimeException {

    public DepartureHasBookingsException(String message) {
        super(message);
    }

}
