package com.tourlk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ItineraryDayRequestDto {

    /** Uniqueness and the 1..durationDays range are validated in the service. */
    @NotNull(message = "Day number is required")
    @Positive(message = "Day number must be a positive number")
    private Integer dayNumber;

    @NotBlank(message = "Day title is required")
    @Size(max = 200, message = "Day title must be at most 200 characters")
    private String title;

    @Size(max = 2000, message = "Day description must be at most 2000 characters")
    private String description;

    /** Optional; blank entries are dropped in the service. */
    private List<@Size(max = 200, message = "Place must be at most 200 characters") String> placesToVisit;

}
