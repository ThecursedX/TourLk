package com.tourlk.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** endDate must be on or after startDate — checked at service level, see VehicleHireServiceImpl. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VehicleHireRequestDto {

    @NotNull(message = "Vehicle is required")
    private Long vehicleId;

    @NotNull(message = "Start date is required")
    @Future(message = "Start date must be in the future")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @NotBlank(message = "Pickup location is required")
    @Size(max = 200, message = "Pickup location must be at most 200 characters")
    private String pickupLocation;

    @Size(max = 1000, message = "Notes must be at most 1000 characters")
    private String notes;

}
