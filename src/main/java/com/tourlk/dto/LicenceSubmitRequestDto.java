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

/** Submitted by a GUIDE or DRIVER via PUT /api/users/me/licence to request verification. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LicenceSubmitRequestDto {

    @NotBlank(message = "Licence number is required")
    @Size(max = 50, message = "Licence number must be at most 50 characters")
    private String licenceNumber;

    @NotNull(message = "Licence expiry date is required")
    @Future(message = "Licence expiry date must be in the future")
    private LocalDate licenceExpiry;

    @NotBlank(message = "A licence document URL is required")
    @Size(max = 1000, message = "Licence document URL must be at most 1000 characters")
    private String licenceDocumentUrl;

}
