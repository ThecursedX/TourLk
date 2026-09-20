package com.tourlk.service;

import com.tourlk.dto.BookingResponseDto;
import com.tourlk.dto.GuideReplyRequestDto;
import com.tourlk.dto.RatingSummaryDto;
import com.tourlk.dto.ReviewRequestDto;
import com.tourlk.dto.ReviewResponseDto;
import com.tourlk.dto.RoomReservationResponseDto;
import com.tourlk.dto.VehicleHireResponseDto;
import com.tourlk.entity.Review;
import com.tourlk.entity.TourPackage;
import com.tourlk.entity.User;
import com.tourlk.enums.BookingStatus;
import com.tourlk.enums.ReviewableType;
import com.tourlk.enums.Role;
import com.tourlk.enums.RoomReservationStatus;
import com.tourlk.enums.VehicleHireStatus;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.DuplicateReviewException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.exception.ReviewNotEligibleException;
import com.tourlk.exception.ReviewableMismatchException;
import com.tourlk.repo.ReviewRepository;
import com.tourlk.repo.TourPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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

    private final ReviewRepository reviewRepository;
    private final BookingService bookingService;
    private final RoomReservationService roomReservationService;
    private final VehicleHireService vehicleHireService;
    private final TourPackageRepository tourPackageRepository;

    @Override
    @Transactional
    public ReviewResponseDto createReview(ReviewRequestDto request, User currentUser) {
        assertEligible(request, currentUser);

        if (reviewRepository.existsByReviewableTypeAndSourceBookingId(
                request.getReviewableType(), request.getSourceBookingId())) {
            throw new DuplicateReviewException("You have already reviewed this experience");
        }

        Review review = Review.builder()
                .reviewer(currentUser)
                .reviewableType(request.getReviewableType())
                .reviewableId(request.getReviewableId())
                .sourceBookingId(request.getSourceBookingId())
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        return toResponse(reviewRepository.save(review));
    }

    @Override
    @Transactional
    public ReviewResponseDto updateReview(Long id, ReviewRequestDto request, User currentUser) {
        Review review = getEntity(id);
        assertReviewer(review, currentUser);

        // reviewableType/reviewableId/sourceBookingId identify which
        // completed experience this review is for and never change —
        // only the rating and comment are editable.
        review.setRating(request.getRating());
        review.setComment(request.getComment());

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

        reviewRepository.delete(review);
    }

    @Override
    public List<ReviewResponseDto> getReviewsFor(ReviewableType reviewableType, Long reviewableId) {
        return reviewRepository.findByReviewableTypeAndReviewableId(reviewableType, reviewableId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public RatingSummaryDto getRatingSummary(ReviewableType reviewableType, Long reviewableId) {
        List<Review> reviews = reviewRepository.findByReviewableTypeAndReviewableId(reviewableType, reviewableId);

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
    @Transactional
    public ReviewResponseDto replyToReview(Long id, GuideReplyRequestDto request, User currentUser) {
        Review review = getEntity(id);
        assertCanReply(review, currentUser);

        review.setGuideReply(request.getReply().trim());
        review.setGuideReplyAt(LocalDateTime.now());

        return toResponse(reviewRepository.save(review));
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
    public List<ReviewResponseDto> getAllReviewsForAdmin(Boolean flaggedOnly) {
        boolean onlyFlagged = Boolean.TRUE.equals(flaggedOnly);
        return reviewRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(review -> !onlyFlagged || Boolean.TRUE.equals(review.getFlagged()))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ReviewResponseDto flagReview(Long id) {
        Review review = getEntity(id);
        review.setFlagged(true);
        return toResponse(reviewRepository.save(review));
    }

    @Override
    @Transactional
    public ReviewResponseDto unflagReview(Long id) {
        Review review = getEntity(id);
        review.setFlagged(false);
        return toResponse(reviewRepository.save(review));
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

    /**
     * getBookingById/getReservationById/getHireById already allow the
     * relevant owner (HOTEL_PARTNER/DRIVER) and ADMIN to fetch the record,
     * not just the tourist — so this extra check is what actually
     * restricts review creation to the tourist who lived the experience.
     */
    private void assertOwnedByCurrentUser(Long touristId, User currentUser) {
        if (!touristId.equals(currentUser.getId())) {
            throw new AccessDeniedException("You can only review your own bookings, reservations or hires");
        }
    }

    private Review getEntity(Long id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));
    }

    private void assertReviewer(Review review, User currentUser) {
        if (!review.getReviewer().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have permission to edit this review");
        }
    }

    private ReviewResponseDto toResponse(Review review) {
        return ReviewResponseDto.builder()
                .id(review.getId())
                .reviewableType(review.getReviewableType())
                .reviewableId(review.getReviewableId())
                .sourceBookingId(review.getSourceBookingId())
                .reviewerId(review.getReviewer().getId())
                .reviewerName(review.getReviewer().getName())
                .rating(review.getRating())
                .comment(review.getComment())
                .flagged(Boolean.TRUE.equals(review.getFlagged()))
                .guideReply(review.getGuideReply())
                .guideReplyAt(review.getGuideReplyAt())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

}
