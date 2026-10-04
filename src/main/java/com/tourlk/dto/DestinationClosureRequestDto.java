package com.tourlk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DestinationClosureRequestDto {

    @NotBlank(message = "Closure reason is required")
    @Size(max = 500, message = "Closure reason must be at most 500 characters")
    private String reason;

    /** First day of the closure; optional (defaults to today). May not be in the past or after {@code until}. */
    private LocalDate from;

    /** Last day of the closure; optional. The destination reopens automatically the day after. */
    private LocalDate until;

}
