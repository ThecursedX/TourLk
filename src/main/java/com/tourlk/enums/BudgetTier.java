package com.tourlk.enums;

/**
 * Price band of a tour package, derived from its price per day (price /
 * durationDays) against the thresholds in {@code app.packages.budget-tiers}.
 * Computed on the fly, never stored — see {@code BudgetTierPolicy}.
 */
public enum BudgetTier {
    BUDGET,
    STANDARD,
    LUXURY
}
