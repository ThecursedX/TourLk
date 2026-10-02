package com.tourlk.exception;

import lombok.Getter;

import java.util.List;

/**
 * Thrown when an edit to an ACTIVE package would change price, duration
 * or capacity while it has upcoming bookings, and the caller hasn't
 * passed {@code confirmChanges=true}. Handled by
 * {@link GlobalExceptionHandler} as HTTP 409 with
 * {@code code: CONFIRMATION_REQUIRED} and the details below, so a client
 * can ask the user and retry with confirmation.
 */
@Getter
public class ChangesRequireConfirmationException extends RuntimeException {

    public static final String CODE = "CONFIRMATION_REQUIRED";

    /** Which of price / durationDays / maxCapacity would change. */
    private final List<String> changedFields;
    private final long affectedBookings;

    public ChangesRequireConfirmationException(List<String> changedFields, long affectedBookings) {
        super("This package has " + affectedBookings + " upcoming booking(s). Changing "
                + String.join(", ", changedFields) + " needs confirmation; existing bookings keep the price "
                + "they were booked at.");
        this.changedFields = List.copyOf(changedFields);
        this.affectedBookings = affectedBookings;
    }

}
