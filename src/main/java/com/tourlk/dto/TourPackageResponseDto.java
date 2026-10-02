package com.tourlk.dto;

import com.tourlk.enums.BudgetTier;
import com.tourlk.enums.PackageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TourPackageResponseDto {

    private Long id;
    private String title;
    private String description;
    /** Nested so the frontend still gets a displayable destination name + id. */
    private DestinationResponseDto destination;
    private int durationDays;
    private BigDecimal price;
    private int maxCapacity;
    private PackageStatus status;
    private Long createdById;
    private String createdByName;
    private LocalDateTime createdAt;
    /**
     * Set while a rejected package awaits resubmission (DRAFT). Only owners
     * and admins can load a non-ACTIVE package, and resubmitting clears it,
     * so it never reaches the public.
     */
    private String rejectionReason;

    private List<ItineraryDayResponseDto> itineraryDays;
    private List<String> inclusions;
    private List<String> exclusions;
    private List<String> imageUrls;

    /**
     * True once the package has any departure (past or upcoming). Bookings
     * must then pick a departure date, even when none are upcoming.
     */
    private boolean hasDepartures;

    /** Rounded to one decimal; 0 when reviewCount is 0. Same numbers as GET /api/reviews/summary. */
    private double averageRating;
    private long reviewCount;
    /** From price per day; thresholds in app.packages.budget-tiers. */
    private BudgetTier budgetTier;

}
