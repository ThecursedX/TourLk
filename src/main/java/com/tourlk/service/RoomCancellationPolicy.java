package com.tourlk.service;

import com.tourlk.config.RoomCancellationPolicyProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * How much of a standalone room reservation's payment a tourist gets back if
 * they cancel today, based on days remaining before check-in. Same tier logic
 * as {@link CancellationPolicy}, with its own thresholds
 * ({@code app.room-cancellation}).
 */
@Component
public class RoomCancellationPolicy {

    private final CancellationPolicy delegate;

    public RoomCancellationPolicy(RoomCancellationPolicyProperties properties) {
        this.delegate = new CancellationPolicy(properties.asCancellationProperties());
    }

    /** 100, the partial percent, or 0, depending on how close check-in is. */
    public int resolveRefundPercent(LocalDate checkInDate) {
        return delegate.resolveRefundPercent(checkInDate);
    }

    public String describeRule(LocalDate checkInDate) {
        return delegate.describeRule(checkInDate).replace("before travel", "before check-in")
                .replace("of travel", "of check-in");
    }
}
