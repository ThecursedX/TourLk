package com.tourlk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RejectBookingRequestDto {

    @NotBlank(message = "A rejection reason is required")
    @Size(max = 1000, message = "Rejection reason must be at most 1000 characters")
    private String reason;

}
