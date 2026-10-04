package com.tourlk.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class BookingRequestDto {

    @NotNull(message = "Tour package is required")
    private Long tourPackageId;

    @NotNull(message = "Travel date is required")
    @Future(message = "Travel date must be in the future")
    private LocalDate travelDate;

    @NotNull(message = "Number of travelers is required")
    @Positive(message = "Number of travelers must be a positive number")
    private Integer numberOfTravelers;

    @Size(max = 1000, message = "Special requests must be at most 1000 characters")
    private String specialRequests;

    /** Optional hotel-room add-ons of the package (dates are derived from the trip). */
    @Valid
    private List<AddOnRoomSelectionDto> addOnRooms;

    /** Optional vehicle add-ons of the package (held for every day of the trip). */
    private List<Long> addOnVehicleIds;

    public BookingRequestDto(Long tourPackageId, LocalDate travelDate, Integer numberOfTravelers,
                             String specialRequests) {
        this.tourPackageId = tourPackageId;
        this.travelDate = travelDate;
        this.numberOfTravelers = numberOfTravelers;
        this.specialRequests = specialRequests;
    }

}
