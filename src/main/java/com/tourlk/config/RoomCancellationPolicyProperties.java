package com.tourlk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Refund tiers for cancelling a standalone room reservation, keyed off days
 * between "now" and its check-in date; same shape as
 * {@link CancellationPolicyProperties}. Bound from {@code app.room-cancellation}.
 */
@ConfigurationProperties(prefix = "app.room-cancellation")
public record RoomCancellationPolicyProperties(
        @DefaultValue("7") int fullRefundMinDays,
        @DefaultValue("2") int partialRefundMinDays,
        @DefaultValue("50") int partialRefundPercent) {

    public RoomCancellationPolicyProperties {
        // Same invariants as the booking policy; validated by constructing it.
        new CancellationPolicyProperties(fullRefundMinDays, partialRefundMinDays, partialRefundPercent);
    }

    public CancellationPolicyProperties asCancellationProperties() {
        return new CancellationPolicyProperties(fullRefundMinDays, partialRefundMinDays, partialRefundPercent);
    }
}
