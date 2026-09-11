package com.tourlk.exception;

/**
 * Thrown when a checkOutDate is not after its checkInDate.
 * Handled by {@link GlobalExceptionHandler} and mapped to HTTP 400.
 */
public class InvalidDateRangeException extends RuntimeException {

    public InvalidDateRangeException(String message) {
        super(message);
    }

}
