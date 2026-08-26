package com.tourlk.exception;

/**
 * Thrown for client-caused failures that aren't bean-validation errors,
 * e.g. duplicate email on registration, bad credentials on login.
 * Handled by {@link GlobalExceptionHandler} and mapped to HTTP 400.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }

}
