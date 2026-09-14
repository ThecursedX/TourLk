package com.tourlk.exception;

/**
 * Thrown when confirming/creating a booking would push the number of
 * travelers booked for a package's travel date past its maxCapacity.
 * Handled by {@link GlobalExceptionHandler} and mapped to HTTP 409.
 */
public class CapacityExceededException extends RuntimeException {

    public CapacityExceededException(String message) {
        super(message);
    }

}
