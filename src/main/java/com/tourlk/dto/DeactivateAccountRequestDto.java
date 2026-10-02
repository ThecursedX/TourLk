package com.tourlk.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeactivateAccountRequestDto {

    /** The caller's current password, re-entered to confirm the deactivation. */
    @NotBlank(message = "Password is required")
    private String password;

}
