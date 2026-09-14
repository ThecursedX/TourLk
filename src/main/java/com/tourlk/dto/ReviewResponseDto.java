package com.tourlk.dto;

import com.tourlk.enums.ReviewableType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponseDto {

    private Long id;
    private ReviewableType reviewableType;
    private Long reviewableId;
    private Long sourceBookingId;
    private Long reviewerId;
    private String reviewerName;
    private int rating;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
