package com.tourlk.exception;

/**
 * Thrown when a requested entity cannot be found.
 * Handled by {@link GlobalExceptionHandler} and mapped to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

}
