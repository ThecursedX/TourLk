package com.tourlk.exception;

/**
 * Thrown when an ADMIN tries to create (or rename) a destination to a
 * name that already exists. Handled by {@link GlobalExceptionHandler}
 * and mapped to HTTP 409.
 */
public class DuplicateDestinationException extends RuntimeException {

    public DuplicateDestinationException(String message) {
        super(message);
    }

}
