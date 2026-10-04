package com.tourlk.util;

import java.time.LocalDate;

/**
 * Dates of the hotel rooms / vehicles added to a package booking, derived from the trip itself:
 * the trip occupies {@code travelDate .. travelDate + durationDays - 1}.
 */
public final class AddOnDates {

    private AddOnDates() {
    }

    /** Rooms are checked in on the travel date. */
    public static LocalDate roomCheckIn(LocalDate travelDate) {
        return travelDate;
    }

    /** {@code durationDays - 1} nights after check-in, but at least one night. */
    public static LocalDate roomCheckOut(LocalDate travelDate, int durationDays) {
        return travelDate.plusDays(Math.max(1, durationDays - 1));
    }

    public static LocalDate vehicleStart(LocalDate travelDate) {
        return travelDate;
    }

    /** The vehicle is held for every day of the trip (inclusive end date). */
    public static LocalDate vehicleEnd(LocalDate travelDate, int durationDays) {
        return travelDate.plusDays(Math.max(1, durationDays) - 1L);
    }

}
