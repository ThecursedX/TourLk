package com.tourlk.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PackageDepartureRequestDto {

    @NotNull(message = "Departure date is required")
    @Future(message = "Departure date must be in the future")
    private LocalDate departureDate;

    /** Optional; defaults to the package's maxCapacity. */
    @Positive(message = "Seats must be a positive number")
    private Integer seatsTotal;

}
