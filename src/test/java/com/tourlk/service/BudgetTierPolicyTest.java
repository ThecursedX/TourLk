package com.tourlk.service;

import com.tourlk.config.BudgetTierProperties;
import com.tourlk.enums.BudgetTier;
import com.tourlk.enums.PackageSort;
import com.tourlk.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BudgetTierPolicy} boundaries, {@link BudgetTierProperties}
 * validation and {@link PackageSort#fromParam} parsing.
 */
class BudgetTierPolicyTest {

    private final BudgetTierPolicy policy =
            new BudgetTierPolicy(new BudgetTierProperties(new BigDecimal("75"), new BigDecimal("200")));

    @Test
    void classify_usesPricePerDay_withInclusiveLowerBounds() {
        assertThat(policy.classify(new BigDecimal("224.99"), 3)).isEqualTo(BudgetTier.BUDGET);   // 74.99/day
        assertThat(policy.classify(new BigDecimal("225.00"), 3)).isEqualTo(BudgetTier.STANDARD); // 75/day
        assertThat(policy.classify(new BigDecimal("599.99"), 3)).isEqualTo(BudgetTier.STANDARD); // 199.99/day
        assertThat(policy.classify(new BigDecimal("600.00"), 3)).isEqualTo(BudgetTier.LUXURY);   // 200/day
    }

    @Test
    void classify_samePriceLongerTrip_isCheaperTier() {
        assertThat(policy.classify(new BigDecimal("400"), 1)).isEqualTo(BudgetTier.LUXURY);
        assertThat(policy.classify(new BigDecimal("400"), 10)).isEqualTo(BudgetTier.BUDGET);
    }

    @Test
    void properties_luxuryNotAboveStandard_failsFast() {
        assertThatThrownBy(() -> new BudgetTierProperties(new BigDecimal("200"), new BigDecimal("200")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BudgetTierProperties(BigDecimal.ZERO, new BigDecimal("200")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sortFromParam_acceptsLowercaseValues_blankMeansNone() {
        assertThat(PackageSort.fromParam("price_asc")).isEqualTo(PackageSort.PRICE_ASC);
        assertThat(PackageSort.fromParam(" Rating ")).isEqualTo(PackageSort.RATING);
        assertThat(PackageSort.fromParam("newest")).isEqualTo(PackageSort.NEWEST);
        assertThat(PackageSort.fromParam(null)).isNull();
        assertThat(PackageSort.fromParam("")).isNull();
    }

    @Test
    void sortFromParam_unknown_throwsBadRequestListingOptions() {
        assertThatThrownBy(() -> PackageSort.fromParam("popular"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("price_asc, price_desc, duration, rating, newest");
    }
}
