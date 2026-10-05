package com.tourlk.dto;

import java.time.LocalDate;

/** An inclusive date range during which a vehicle is held by a PENDING or CONFIRMED hire. */
public record BookedDateRangeDto(LocalDate startDate, LocalDate endDate) {
}
