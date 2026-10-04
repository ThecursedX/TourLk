package com.tourlk.dto;

import com.tourlk.enums.BudgetTier;
import com.tourlk.enums.PackageSort;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Optional filters + sort for browsing active tour packages
 * ({@code GET /api/packages}). Every field may be null, meaning "don't
 * filter on this". Ranges and blank text are validated/normalised in
 * {@code TourPackageServiceImpl#browsePackages}.
 */
@Getter
@Builder
public class PackageSearchCriteria {

    private final Long destinationId;
    private final BigDecimal minPrice;
    private final BigDecimal maxPrice;
    /** Case-insensitive substring of the title or description. */
    private final String q;
    private final Integer minDays;
    private final Integer maxDays;
    private final BudgetTier budgetTier;
    /** Keeps packages with a departure on/after this date, plus packages with no departures at all. */
    private final LocalDate travelDate;
    private final PackageSort sort;

}
