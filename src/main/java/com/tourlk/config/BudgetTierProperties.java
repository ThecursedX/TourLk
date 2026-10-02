package com.tourlk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

/**
 * Price-per-day thresholds (USD) for {@link com.tourlk.enums.BudgetTier}:
 * below {@code standardMinPerDay} is BUDGET, below {@code luxuryMinPerDay}
 * is STANDARD, anything else is LUXURY. Bound from
 * {@code app.packages.budget-tiers} in application.yml.
 */
@ConfigurationProperties(prefix = "app.packages.budget-tiers")
public record BudgetTierProperties(
        @DefaultValue("75") BigDecimal standardMinPerDay,
        @DefaultValue("200") BigDecimal luxuryMinPerDay) {

    public BudgetTierProperties {
        if (standardMinPerDay.signum() <= 0 || luxuryMinPerDay.compareTo(standardMinPerDay) <= 0) {
            throw new IllegalArgumentException("app.packages.budget-tiers must satisfy "
                    + "0 < standard-min-per-day < luxury-min-per-day, got "
                    + standardMinPerDay + " / " + luxuryMinPerDay);
        }
    }
}
