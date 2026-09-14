package com.tourlk.exception;

/**
 * Thrown when a user tries to view or reply to a support ticket that
 * isn't theirs and they aren't an ADMIN — e.g. guessing another
 * tourist's ticket id. Handled by {@link GlobalExceptionHandler} and
 * mapped to HTTP 403.
 */
public class TicketAccessDeniedException extends RuntimeException {

    public TicketAccessDeniedException(String message) {
        super(message);
    }

}
