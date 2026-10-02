package com.tourlk.service;

import com.tourlk.config.CancellationPolicyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * How much of a booking's payment is refundable if cancelled today, based
 * on how many days remain before its travelDate. See
 * {@link CancellationPolicyProperties} for the configured thresholds.
 */
@Component
@RequiredArgsConstructor
public class CancellationPolicy {

    private final CancellationPolicyProperties properties;

    /** 100, {@code partialRefundPercent}, or 0, depending on how close travelDate is. */
    public int resolveRefundPercent(LocalDate travelDate) {
        long daysUntilTravel = ChronoUnit.DAYS.between(LocalDate.now(), travelDate);
        if (daysUntilTravel >= properties.fullRefundMinDays()) {
            return 100;
        }
        if (daysUntilTravel >= properties.partialRefundMinDays()) {
            return properties.partialRefundPercent();
        }
        return 0;
    }

    /** Human-readable explanation of the rule that applies to a cancellation today. */
    public String describeRule(LocalDate travelDate) {
        return switch (resolveRefundPercent(travelDate)) {
            case 100 -> "Cancelled " + properties.fullRefundMinDays()
                    + "+ days before travel: full refund.";
            case 0 -> "Cancelled within " + properties.partialRefundMinDays()
                    + " day(s) of travel: no refund.";
            default -> "Cancelled " + properties.partialRefundMinDays() + "-"
                    + (properties.fullRefundMinDays() - 1) + " days before travel: "
                    + properties.partialRefundPercent() + "% refund.";
        };
    }
}
