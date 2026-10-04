package com.tourlk.service;

import com.tourlk.dto.BookingResponseDto;
import com.tourlk.dto.GuideReplyRequestDto;
import com.tourlk.dto.GuideReviewStatsDto;
import com.tourlk.dto.RatingSummaryDto;
import com.tourlk.dto.ReviewEditHistoryResponseDto;
import com.tourlk.dto.ReviewRequestDto;
import com.tourlk.dto.ReviewResponseDto;
import com.tourlk.dto.RoomReservationResponseDto;
import com.tourlk.dto.VehicleHireResponseDto;
import com.tourlk.entity.Review;
import com.tourlk.entity.ReviewEditHistory;
import com.tourlk.entity.TourPackage;
import com.tourlk.entity.User;
import com.tourlk.enums.BookingStatus;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.ReviewStatus;
import com.tourlk.enums.ReviewableType;
import com.tourlk.enums.Role;
import com.tourlk.enums.RoomReservationStatus;
import com.tourlk.enums.VehicleHireStatus;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.DuplicateReviewException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.exception.ReviewNotEligibleException;
import com.tourlk.exception.ReviewableMismatchException;
import com.tourlk.repo.ReviewEditHistoryRepository;
import com.tourlk.repo.ReviewRepository;
import com.tourlk.repo.TourPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reviews are only ever created off the back of a completed Booking /
 * RoomReservation / VehicleHire — see {@code createReview} /
 * {@code assertEligible} for the chain that enforces this: the source
 * record must belong to the current user, must be COMPLETED, and its
 * package/accommodation/vehicle must actually match the reviewableId the
 * client sent (otherwise a tourist could review item B using a
 * completed booking for unrelated item A).
 */
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final long EDIT_WINDOW_DAYS = 30;

    private final ReviewRepository reviewRepository;
    private final ReviewEditHistoryRepository reviewEditHistoryRepository;
    private final BookingService bookingService;
    private final RoomReservationService roomReservationService;
    private final VehicleHireService vehicleHireService;
    private final TourPackageRepository tourPackageRepository;
    private final NotificationService notificationService;
    private final ProfanityFilterService profanityFilterService;

    @Override
    @Transactional
    public ReviewResponseDto createReview(ReviewRequestDto request, User currentUser) {
        assertEligible(request, currentUser);

        if (reviewRepository.existsByReviewableTypeAndSourceBookingIdAndStatusNot(
                request.getReviewableType(), request.getSourceBookingId(), ReviewStatus.DELETED)) {
            throw new DuplicateReviewException("You have already reviewed this experience");
        }

        boolean profane = profanityFilterService.containsProfanity(request.getComment());

        Review review = Review.builder()
                .reviewer(currentUser)
                .reviewableType(request.getReviewableType())
                .reviewableId(request.getReviewableId())
                .sourceBookingId(request.getSourceBookingId())
                .rating(request.getRating())
                .comment(request.getComment())
                .imageUrls(normalizeImageUrls(request.getImageUrls()))
                .status(profane ? ReviewStatus.REPORTED : ReviewStatus.PUBLISHED)
                .build();

        review = reviewRepository.save(review);

        notificationService.notifyAdmins(NotificationType.REVIEW_POSTED, "New review posted",
                currentUser.getName() + " left a " + review.getRating() + "-star review"
                        + (profane ? " (auto-reported for profanity)" : ""),
                "/admin/reviews");

        return toResponse(review);
    }

    @Override
    @Transactional
    public ReviewResponseDto updateReview(Long id, ReviewRequestDto request, User currentUser) {
        Review review = getEntity(id);
        assertReviewer(review, currentUser);
        assertWithinEditWindow(review);

        reviewEditHistoryRepository.save(ReviewEditHistory.builder()
                .review(review)
                .oldRating(review.getRating())
                .oldComment(review.getComment())
                .build());

        // reviewableType/reviewableId/sourceBookingId identify which
        // completed experience this review is for and never change —
        // only the rating, comment and images are editable.
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setImageUrls(normalizeImageUrls(request.getImageUrls()));
        review.setStatus(profanityFilterService.containsProfanity(request.getComment())
                ? ReviewStatus.REPORTED
                : ReviewStatus.EDITED);

        return toResponse(reviewRepository.save(review));
    }

    @Override
    @Transactional
    public void deleteReview(Long id, User currentUser) {
        Review review = getEntity(id);

        boolean isReviewer = review.getReviewer().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        if (!isReviewer && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to delete this review");
        }

        if (isReviewer) {
            reviewRepository.delete(review);
        } else {
            // An admin removing someone else's review is moderation, not
            // self-service cleanup — soft-delete so the row (and its edit
            // history) survives for audit, and so it stops blocking the
            // reviewer from leaving a fresh review (see the duplicate check
            // in createReview, which ignores DELETED rows).
            review.setStatus(ReviewStatus.DELETED);
            reviewRepository.save(review);
        }
    }

    @Override
    public List<ReviewResponseDto> getReviewsFor(ReviewableType reviewableType, Long reviewableId) {
        return reviewRepository
                .findByReviewableTypeAndReviewableIdAndStatusNotIn(
                        reviewableType, reviewableId, ReviewStatus.HIDDEN_FROM_PUBLIC)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public RatingSummaryDto getRatingSummary(ReviewableType reviewableType, Long reviewableId) {
        List<Review> reviews = reviewRepository
                .findByReviewableTypeAndReviewableIdAndStatusNotIn(
                        reviewableType, reviewableId, ReviewStatus.HIDDEN_FROM_PUBLIC);

        double average = reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);

        return RatingSummaryDto.builder()
                .reviewableType(reviewableType)
                .reviewableId(reviewableId)
                .averageRating(Math.round(average * 10) / 10.0)
                .totalReviews(reviews.size())
                .build();
    }

    @Override
    public List<ReviewResponseDto> getReviewsByUser(Long userId) {
        return reviewRepository.findByReviewerId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<ReviewEditHistoryResponseDto> getEditHistory(Long reviewId, User currentUser) {
        Review review = getEntity(reviewId);
        boolean isReviewer = review.getReviewer().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        if (!isReviewer && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to view this review's edit history");
        }

        return reviewEditHistoryRepository.findByReviewIdOrderByCreatedAtDesc(reviewId).stream()
                .map(h -> ReviewEditHistoryResponseDto.builder()
                        .id(h.getId())
                        .oldRating(h.getOldRating())
                        .oldComment(h.getOldComment())
                        .editedAt(h.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public ReviewResponseDto replyToReview(Long id, GuideReplyRequestDto request, User currentUser) {
        Review review = getEntity(id);
        assertCanReply(review, currentUser);

        review.setGuideReply(request.getReply().trim());
        review.setGuideReplyAt(LocalDateTime.now());
        review = reviewRepository.save(review);

        notificationService.notify(review.getReviewer(), NotificationType.REVIEW_GUIDE_REPLY,
                "The guide replied to your review", review.getGuideReply(), reviewLink(review));

        return toResponse(review);
    }

    @Override
    @Transactional
    public ReviewResponseDto removeGuideReply(Long id, User currentUser) {
        Review review = getEntity(id);
        assertCanReply(review, currentUser);

        review.setGuideReply(null);
        review.setGuideReplyAt(null);

        return toResponse(reviewRepository.save(review));
    }

    @Override
    public List<ReviewResponseDto> getAllReviewsForAdmin(ReviewStatus status) {
        return reviewRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(review -> status == null || review.getStatus() == status)
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ReviewResponseDto reportReview(Long id) {
        Review review = getEntity(id);
        review.setStatus(ReviewStatus.REPORTED);
        return toResponse(reviewRepository.save(review));
    }

    @Override
    @Transactional
    public ReviewResponseDto unreportReview(Long id) {
        Review review = getEntity(id);
        review.setStatus(restingStatus(review));
        return toResponse(reviewRepository.save(review));
    }

    @Override
    @Transactional
    public ReviewResponseDto hideReview(Long id) {
        Review review = getEntity(id);
        review.setStatus(ReviewStatus.HIDDEN);
        return toResponse(reviewRepository.save(review));
    }

    @Override
    @Transactional
    public ReviewResponseDto unhideReview(Long id) {
        Review review = getEntity(id);
        review.setStatus(restingStatus(review));
        return toResponse(reviewRepository.save(review));
    }

    @Override
    public GuideReviewStatsDto getGuideStats(User guide) {
        List<Long> packageIds = tourPackageRepository.findByCreatedById(guide.getId()).stream()
                .map(TourPackage::getId)
                .toList();

        List<Review> reviews = packageIds.isEmpty()
                ? List.of()
                : reviewRepository.findByReviewableTypeAndReviewableIdInAndStatusNotIn(
                        ReviewableType.TOUR_PACKAGE, packageIds, ReviewStatus.HIDDEN_FROM_PUBLIC);

        Map<Integer, Long> countByStar = new LinkedHashMap<>();
        for (int star = 1; star <= 5; star++) {
            countByStar.put(star, 0L);
        }
        for (Review review : reviews) {
            countByStar.merge(review.getRating(), 1L, Long::sum);
        }

        double average = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
        long repliedCount = reviews.stream().filter(r -> r.getGuideReply() != null).count();
        double replyRate = reviews.isEmpty() ? 0.0 : (repliedCount * 100.0) / reviews.size();

        return GuideReviewStatsDto.builder()
                .averageRating(Math.round(average * 10) / 10.0)
                .totalReviews(reviews.size())
                .countByStar(countByStar)
                .replyRate(Math.round(replyRate * 10) / 10.0)
                .build();
    }

    /** What a REPORTED/HIDDEN review reverts to — EDITED if it has ever been edited, else PUBLISHED. */
    private ReviewStatus restingStatus(Review review) {
        return reviewEditHistoryRepository.existsByReviewId(review.getId()) ? ReviewStatus.EDITED : ReviewStatus.PUBLISHED;
    }

    private List<String> normalizeImageUrls(List<String> imageUrls) {
        return imageUrls == null ? List.of() : imageUrls;
    }

    private void assertWithinEditWindow(Review review) {
        if (review.getCreatedAt() == null) {
            return;
        }
        LocalDateTime deadline = review.getCreatedAt().plusDays(EDIT_WINDOW_DAYS);
        if (LocalDateTime.now().isAfter(deadline)) {
            throw new BadRequestException(
                    "This review can no longer be edited — edits are only allowed within "
                            + EDIT_WINDOW_DAYS + " days of posting");
        }
    }

    /**
     * Only TOUR_PACKAGE reviews take a guide reply, and only from the guide
     * who created that package (or an ADMIN).
     */
    private void assertCanReply(Review review, User currentUser) {
        if (review.getReviewableType() != ReviewableType.TOUR_PACKAGE) {
            throw new BadRequestException("Only tour package reviews can receive a guide reply");
        }
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        TourPackage tourPackage = tourPackageRepository.findById(review.getReviewableId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tour package not found with id: " + review.getReviewableId()));
        if (!tourPackage.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You can only reply to reviews of your own tour packages");
        }
    }

    private void assertEligible(ReviewRequestDto request, User currentUser) {
        Long sourceId = request.getSourceBookingId();
        Long reviewableId = request.getReviewableId();

        switch (request.getReviewableType()) {
            case TOUR_PACKAGE -> {
                BookingResponseDto booking = bookingService.getBookingById(sourceId, currentUser);
                assertOwnedByCurrentUser(booking.getTouristId(), currentUser);
                if (booking.getStatus() != BookingStatus.COMPLETED) {
                    throw new ReviewNotEligibleException(
                            "You can only review a tour package once your booking for it is completed");
                }
                if (!booking.getTourPackage().getId().equals(reviewableId)) {
                    throw new ReviewableMismatchException(
                            "This booking is not for the tour package you're trying to review");
                }
            }
            case ACCOMMODATION -> {
                RoomReservationResponseDto reservation =
                        roomReservationService.getReservationById(sourceId, currentUser);
                assertOwnedByCurrentUser(reservation.getTouristId(), currentUser);
                if (reservation.getStatus() != RoomReservationStatus.COMPLETED) {
                    throw new ReviewNotEligibleException(
                            "You can only review an accommodation once your reservation there is completed");
                }
                if (!reservation.getRoom().getAccommodationId().equals(reviewableId)) {
                    throw new ReviewableMismatchException(
                            "This reservation is not for the accommodation you're trying to review");
                }
            }
            case VEHICLE -> {
                VehicleHireResponseDto hire = vehicleHireService.getHireById(sourceId, currentUser);
                assertOwnedByCurrentUser(hire.getTouristId(), currentUser);
                if (hire.getStatus() != VehicleHireStatus.COMPLETED) {
                    throw new ReviewNotEligibleException(
                            "You can only review a vehicle once your hire of it is completed");
                }
                if (!hire.getVehicle().getId().equals(reviewableId)) {
                    throw new ReviewableMismatchException(
                            "This hire is not for the vehicle you're trying to review");
                }
            }
        }
    }

    private void assertOwnedByCurrentUser(Long touristId, User currentUser) {
        if (!touristId.equals(currentUser.getId())) {
            throw new AccessDeniedException("You can only review your own bookings, reservations or hires");
        }
    }

    private Review getEntity(Long id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));
    }

    private String reviewLink(Review review) {
        return switch (review.getReviewableType()) {
            case TOUR_PACKAGE -> "/packages/" + review.getReviewableId();
            case ACCOMMODATION -> "/accommodations/" + review.getReviewableId();
            case VEHICLE -> "/vehicles/" + review.getReviewableId();
        };
    }

    private void assertReviewer(Review review, User currentUser) {
        if (!review.getReviewer().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have permission to edit this review");
        }
    }

    private ReviewResponseDto toResponse(Review review) {
        LocalDateTime editDeadline = review.getCreatedAt() == null
                ? null
                : review.getCreatedAt().plusDays(EDIT_WINDOW_DAYS);

        return ReviewResponseDto.builder()
                .id(review.getId())
                .reviewableType(review.getReviewableType())
                .reviewableId(review.getReviewableId())
                .sourceBookingId(review.getSourceBookingId())
                .reviewerId(review.getReviewer().getId())
                .reviewerName(review.getReviewer().getName())
                .rating(review.getRating())
                .comment(review.getComment())
                .imageUrls(review.getImageUrls())
                .status(review.getStatus())
                .editable(editDeadline != null && LocalDateTime.now().isBefore(editDeadline))
                .editDeadline(editDeadline)
                .guideReply(review.getGuideReply())
                .guideReplyAt(review.getGuideReplyAt())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

}
