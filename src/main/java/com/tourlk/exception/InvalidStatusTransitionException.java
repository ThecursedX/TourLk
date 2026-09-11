package com.tourlk.exception;

/**
 * Thrown when an action would move an entity's status field to a state
 * it cannot legally reach from its current state, e.g. approving a
 * package that isn't PENDING_APPROVAL.
 * Handled by {@link GlobalExceptionHandler} and mapped to HTTP 409.
 */
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }

}
