package com.tourlk.service;

import com.tourlk.dto.GuideReplyRequestDto;
import com.tourlk.dto.GuideReviewStatsDto;
import com.tourlk.dto.RatingSummaryDto;
import com.tourlk.dto.ReviewEditHistoryResponseDto;
import com.tourlk.dto.ReviewRequestDto;
import com.tourlk.dto.ReviewResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.ReviewStatus;
import com.tourlk.enums.ReviewableType;

import java.util.List;

public interface ReviewService {

    ReviewResponseDto createReview(ReviewRequestDto request, User currentUser);

    /** @throws com.tourlk.exception.BadRequestException if the 30-day edit window has passed */
    ReviewResponseDto updateReview(Long id, ReviewRequestDto request, User currentUser);

    void deleteReview(Long id, User currentUser);

    List<ReviewResponseDto> getReviewsFor(ReviewableType reviewableType, Long reviewableId);

    RatingSummaryDto getRatingSummary(ReviewableType reviewableType, Long reviewableId);

    List<ReviewResponseDto> getReviewsByUser(Long userId);

    /** Every edit ever made to this review, newest first. Only the reviewer or an ADMIN may view it. */
    List<ReviewEditHistoryResponseDto> getEditHistory(Long reviewId, User currentUser);

    /**
     * Sets or replaces the guide's reply on a TOUR_PACKAGE review. Allowed
     * for the guide who created the reviewed package, and for ADMINs.
     */
    ReviewResponseDto replyToReview(Long id, GuideReplyRequestDto request, User currentUser);

    /** Removes the guide's reply; same permissions as {@link #replyToReview}. */
    ReviewResponseDto removeGuideReply(Long id, User currentUser);

    /** ADMIN moderation view, newest first; pass {@code null} for every status. */
    List<ReviewResponseDto> getAllReviewsForAdmin(ReviewStatus status);

    ReviewResponseDto reportReview(Long id);

    /** Reverts a REPORTED review to PUBLISHED/EDITED (whichever it last was). */
    ReviewResponseDto unreportReview(Long id);

    ReviewResponseDto hideReview(Long id);

    /** Reverts a HIDDEN review to PUBLISHED/EDITED (whichever it last was). */
    ReviewResponseDto unhideReview(Long id);

    /** Average rating, per-star counts and reply rate across every package the guide owns. */
    GuideReviewStatsDto getGuideStats(User guide);

}
