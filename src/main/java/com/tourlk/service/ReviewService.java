package com.tourlk.service;

import com.tourlk.dto.GuideReplyRequestDto;
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

    /**
     * Sets or replaces the guide's reply on a TOUR_PACKAGE review. Allowed
     * for the guide who created the reviewed package, and for ADMINs.
     */
    ReviewResponseDto replyToReview(Long id, GuideReplyRequestDto request, User currentUser);

    /** Removes the guide's reply; same permissions as {@link #replyToReview}. */
    ReviewResponseDto removeGuideReply(Long id, User currentUser);

    /** ADMIN moderation view, newest first; {@code flaggedOnly = true} narrows to flagged reviews. */
    List<ReviewResponseDto> getAllReviewsForAdmin(Boolean flaggedOnly);

    ReviewResponseDto flagReview(Long id);

    ReviewResponseDto unflagReview(Long id);

}
