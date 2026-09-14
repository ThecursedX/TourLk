package com.tourlk.config;

import com.stripe.Stripe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Sets the Stripe SDK's static API key from {@code stripe.secret-key} at
 * startup. Use a TEST MODE key (starts with {@code sk_test_}) — see
 * frontend/README.md for where to get one and how to run the webhook
 * locally with the Stripe CLI.
 */
@Configuration
public class StripeConfig {

    public StripeConfig(@Value("${stripe.secret-key}") String secretKey) {
        if (StringUtils.hasText(secretKey)) {
            Stripe.apiKey = secretKey;
        }
    }

}
