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
public class SavePaymentMethodRequestDto {

    @NotBlank(message = "paymentMethodId is required")
    private String paymentMethodId;

}
