package com.tourlk.exception;

/**
 * Thrown when creating/confirming a hire for a vehicle that already has
 * a CONFIRMED hire overlapping the requested date range.
 * Handled by {@link GlobalExceptionHandler} and mapped to HTTP 409.
 */
public class VehicleUnavailableException extends RuntimeException {

    public VehicleUnavailableException(String message) {
        super(message);
    }

}
