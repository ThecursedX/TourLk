package com.tourlk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethodResponseDto {

    private Long id;
    private String brand;
    private String last4;
    private Integer expMonth;
    private Integer expYear;
    private boolean defaultCard;

}
