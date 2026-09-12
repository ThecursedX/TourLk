package com.tourlk.service;

import com.tourlk.dto.RatingSummaryDto;
import com.tourlk.dto.ReviewRequestDto;
import com.tourlk.dto.ReviewResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.ReviewableType;

import java.util.List;

public interface ReviewService {

    ReviewResponseDto createReview(ReviewRequestDto request, User currentUser);

    ReviewResponseDto updateReview(Long id, ReviewRequestDto request, User currentUser);

    void deleteReview(Long id, User currentUser);

    List<ReviewResponseDto> getReviewsFor(ReviewableType reviewableType, Long reviewableId);

    RatingSummaryDto getRatingSummary(ReviewableType reviewableType, Long reviewableId);

    List<ReviewResponseDto> getReviewsByUser(Long userId);

}
