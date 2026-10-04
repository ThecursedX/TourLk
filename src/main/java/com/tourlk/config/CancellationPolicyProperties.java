package com.tourlk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Refund tiers for booking cancellation, keyed off days between "now" and
 * the booking's travelDate: {@code fullRefundMinDays}+ days out gives a
 * full refund, {@code partialRefundMinDays}-{@code fullRefundMinDays}
 * gives {@code partialRefundPercent}%, and anything closer gives 0%.
 * Bound from {@code app.cancellation} in application.yml.
 */
@ConfigurationProperties(prefix = "app.cancellation")
public record CancellationPolicyProperties(
        @DefaultValue("7") int fullRefundMinDays,
        @DefaultValue("3") int partialRefundMinDays,
        @DefaultValue("50") int partialRefundPercent) {

    public CancellationPolicyProperties {
        if (partialRefundMinDays <= 0 || fullRefundMinDays <= partialRefundMinDays) {
            throw new IllegalArgumentException("app.cancellation must satisfy "
                    + "0 < partial-refund-min-days < full-refund-min-days, got "
                    + partialRefundMinDays + " / " + fullRefundMinDays);
        }
        if (partialRefundPercent < 0 || partialRefundPercent > 100) {
            throw new IllegalArgumentException(
                    "app.cancellation.partial-refund-percent must be between 0 and 100, got "
                            + partialRefundPercent);
        }
    }
}
