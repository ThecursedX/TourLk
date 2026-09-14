package com.tourlk.exception;

/**
 * Thrown when a new (or updated) TourPackage/Accommodation tries to
 * reference a destination that is INACTIVE. Existing packages/
 * accommodations keep their reference to a deactivated destination — this
 * only blocks selecting one for new/edited listings. Handled by
 * {@link GlobalExceptionHandler} and mapped to HTTP 409.
 */
public class DestinationInactiveException extends RuntimeException {

    public DestinationInactiveException(String message) {
        super(message);
    }

}
