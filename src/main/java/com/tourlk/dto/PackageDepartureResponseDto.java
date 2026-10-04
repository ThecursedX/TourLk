package com.tourlk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PackageDepartureResponseDto {

    private Long id;
    private LocalDate departureDate;
    private int seatsTotal;
    /** seatsTotal minus travelers on CONFIRMED/RESCHEDULED bookings for this date; never negative. */
    private int seatsLeft;

}
