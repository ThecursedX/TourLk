package com.tourlk.dto;

import com.tourlk.enums.ReviewableType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RatingSummaryDto {

    private ReviewableType reviewableType;
    private Long reviewableId;
    private double averageRating;
    private long totalReviews;

}
