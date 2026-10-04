package com.tourlk.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AddOnDatesTest {

    private final LocalDate travel = LocalDate.of(2026, 10, 12);

    @Test
    void rooms_checkOutIsDurationMinusOneNightsAfterCheckIn() {
        assertThat(AddOnDates.roomCheckIn(travel)).isEqualTo(travel);
        assertThat(AddOnDates.roomCheckOut(travel, 4)).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void rooms_getAtLeastOneNight_forOneDayPackages() {
        assertThat(AddOnDates.roomCheckOut(travel, 1)).isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(AddOnDates.roomCheckOut(travel, 2)).isEqualTo(LocalDate.of(2026, 10, 13));
    }

    @Test
    void vehicles_areHeldForEveryDayOfTheTripInclusive() {
        assertThat(AddOnDates.vehicleStart(travel)).isEqualTo(travel);
        assertThat(AddOnDates.vehicleEnd(travel, 4)).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(AddOnDates.vehicleEnd(travel, 1)).isEqualTo(travel);
    }

}
