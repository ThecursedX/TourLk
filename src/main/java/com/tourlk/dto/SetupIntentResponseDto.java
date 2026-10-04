package com.tourlk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Returned to the frontend so Stripe.js can collect and save a card
 * client-side via {@code stripe.confirmSetup()} with this clientSecret.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetupIntentResponseDto {

    private String clientSecret;

}
