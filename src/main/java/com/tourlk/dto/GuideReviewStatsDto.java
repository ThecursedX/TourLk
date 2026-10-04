package com.tourlk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/** Aggregate review stats across every tour package the logged-in guide owns. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuideReviewStatsDto {

    private double averageRating;
    private long totalReviews;
    /** Number of reviews at each star rating, keyed 1-5. */
    private Map<Integer, Long> countByStar;
    /** Percentage (0-100) of reviews the guide has replied to. */
    private double replyRate;

}
