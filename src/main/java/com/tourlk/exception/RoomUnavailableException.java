package com.tourlk.exception;

/**
 * Thrown when confirming/creating a reservation would exceed a room
 * type's totalRooms for some night in the requested date range.
 * Handled by {@link GlobalExceptionHandler} and mapped to HTTP 409.
 */
public class RoomUnavailableException extends RuntimeException {

    public RoomUnavailableException(String message) {
        super(message);
    }

}
