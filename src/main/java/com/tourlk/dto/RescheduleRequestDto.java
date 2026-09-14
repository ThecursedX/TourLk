package com.tourlk.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RescheduleRequestDto {

    @NotNull(message = "New travel date is required")
    @Future(message = "New travel date must be in the future")
    private LocalDate newTravelDate;

}
