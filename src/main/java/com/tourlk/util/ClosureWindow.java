package com.tourlk.util;

import com.tourlk.entity.Destination;
import com.tourlk.enums.DestinationStatus;

import java.time.LocalDate;
import java.util.Optional;

/**
 * The dates a destination is closed: from {@code from} (inclusive) until
 * {@code until} (inclusive), or open-ended when {@code until} is null.
 * A closure with no explicit start begins today.
 */
public record ClosureWindow(LocalDate from, LocalDate until) {

    /** The window of a TEMPORARILY_CLOSED destination; empty for any other status. */
    public static Optional<ClosureWindow> of(Destination destination, LocalDate today) {
        if (destination == null || destination.getStatus() != DestinationStatus.TEMPORARILY_CLOSED) {
            return Optional.empty();
        }
        return Optional.of(of(destination.getClosureFrom(), destination.getClosureUntil(), today));
    }

    public static ClosureWindow of(LocalDate from, LocalDate until, LocalDate today) {
        return new ClosureWindow(from != null ? from : today, until);
    }

    /** Last day of a trip that starts on {@code travelDate} and lasts {@code durationDays} days. */
    public static LocalDate tripEnd(LocalDate travelDate, int durationDays) {
        return travelDate.plusDays(Math.max(durationDays, 1) - 1L);
    }

    /** True when the trip's date range shares at least one day with the closure. */
    public boolean overlaps(LocalDate travelDate, int durationDays) {
        LocalDate tripEnd = tripEnd(travelDate, durationDays);
        return !travelDate.isAfter(until == null ? LocalDate.MAX : until) && !tripEnd.isBefore(from);
    }

    /** "from 2026-10-10 to 2026-10-20", or "from 2026-10-10 until further notice" when open-ended. */
    public String describe() {
        return until == null ? "from " + from + " until further notice" : "from " + from + " to " + until;
    }

}
