package com.tourlk.exception;

/**
 * Thrown when someone tries to reply to a CLOSED support ticket — it
 * must be reopened first. Handled by {@link GlobalExceptionHandler} and
 * mapped to HTTP 409.
 */
public class TicketClosedException extends RuntimeException {

    public TicketClosedException(String message) {
        super(message);
    }

}
