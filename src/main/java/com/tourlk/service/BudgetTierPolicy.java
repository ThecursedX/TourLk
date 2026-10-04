package com.tourlk.service;

import com.tourlk.config.BudgetTierProperties;
import com.tourlk.enums.BudgetTier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Classifies a package into a {@link BudgetTier} by price per day.
 * Compares {@code price} against {@code threshold * durationDays} instead
 * of dividing, so it agrees exactly with the browse filter's SQL
 * predicate (see {@code TourPackageSpecifications#inBudgetTier}) with no
 * rounding differences at the boundaries.
 */
@Component
@RequiredArgsConstructor
public class BudgetTierPolicy {

    private final BudgetTierProperties properties;

    public BudgetTier classify(BigDecimal price, int durationDays) {
        BigDecimal days = BigDecimal.valueOf(Math.max(durationDays, 1));
        if (price.compareTo(properties.standardMinPerDay().multiply(days)) < 0) {
            return BudgetTier.BUDGET;
        }
        if (price.compareTo(properties.luxuryMinPerDay().multiply(days)) < 0) {
            return BudgetTier.STANDARD;
        }
        return BudgetTier.LUXURY;
    }

    public BigDecimal standardMinPerDay() {
        return properties.standardMinPerDay();
    }

    public BigDecimal luxuryMinPerDay() {
        return properties.luxuryMinPerDay();
    }
}
