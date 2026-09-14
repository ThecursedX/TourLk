package com.tourlk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Returned to the frontend so Stripe.js can complete the payment
 * client-side via {@code stripe.confirmPayment()} with this clientSecret.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentIntentResponseDto {

    private String clientSecret;
    private Long paymentId;

}
