package com.tourlk.exception;

/**
 * Thrown when a GUIDE tries to create a tour package, or a DRIVER tries to
 * register a vehicle, before their licence has reached VERIFIED status.
 * Handled by {@link GlobalExceptionHandler} and mapped to HTTP 403.
 */
public class LicenceNotVerifiedException extends RuntimeException {

    public LicenceNotVerifiedException(String message) {
        super(message);
    }

}
