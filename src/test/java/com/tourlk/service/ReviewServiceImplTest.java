package com.tourlk.service;

import com.tourlk.dto.BookingPackageSummaryDto;
import com.tourlk.dto.BookingResponseDto;
import com.tourlk.dto.RatingSummaryDto;
import com.tourlk.dto.ReviewRequestDto;
import com.tourlk.dto.ReviewResponseDto;
import com.tourlk.dto.RoomReservationResponseDto;
import com.tourlk.dto.RoomSummaryDto;
import com.tourlk.entity.Review;
import com.tourlk.entity.User;
import com.tourlk.enums.BookingStatus;
import com.tourlk.enums.ReviewableType;
import com.tourlk.enums.Role;
import com.tourlk.enums.RoomReservationStatus;
import com.tourlk.exception.DuplicateReviewException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.exception.ReviewNotEligibleException;
import com.tourlk.exception.ReviewableMismatchException;
import com.tourlk.repo.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReviewServiceImpl}: the eligibility chain for
 * creating a review (owned + completed + matching source), the
 * duplicate-review guard, and the reviewer/admin checks on update/delete.
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private BookingService bookingService;
    @Mock
    private RoomReservationService roomReservationService;
    @Mock
    private VehicleHireService vehicleHireService;

    @InjectMocks
    private ReviewServiceImpl service;

    private User reviewer;
    private User stranger;
    private User admin;

    @BeforeEach
    void setUp() {
        reviewer = User.builder().id(1L).name("Tess").role(Role.TOURIST).build();
        stranger = User.builder().id(2L).name("Stan").role(Role.TOURIST).build();
        admin = User.builder().id(9L).name("Amy Admin").role(Role.ADMIN).build();
    }

    private ReviewRequestDto packageReview() {
        return new ReviewRequestDto(ReviewableType.TOUR_PACKAGE, 100L, 500L, 5, "Superb");
    }

    private BookingResponseDto completedBookingFor(Long touristId, Long packageId, BookingStatus status) {
        return BookingResponseDto.builder()
                .id(500L).touristId(touristId).status(status)
                .tourPackage(BookingPackageSummaryDto.builder().id(packageId).title("Trip").build())
                .build();
    }

    private Review persistedReview(User owner) {
        return Review.builder()
                .id(7L).reviewer(owner).reviewableType(ReviewableType.TOUR_PACKAGE)
                .reviewableId(100L).sourceBookingId(500L).rating(4).comment("ok")
                .build();
    }

    // ------------------------------------------------------------------
    // createReview — eligibility chain
    // ------------------------------------------------------------------

    @Test
    void createReview_completedOwnedMatchingBooking_savesReview() {
        when(bookingService.getBookingById(500L, reviewer))
                .thenReturn(completedBookingFor(1L, 100L, BookingStatus.COMPLETED));
        when(reviewRepository.existsByReviewableTypeAndSourceBookingId(ReviewableType.TOUR_PACKAGE, 500L))
                .thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(7L);
            return r;
        });

        ReviewResponseDto result = service.createReview(packageReview(), reviewer);

        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getRating()).isEqualTo(5);
        assertThat(result.getReviewerId()).isEqualTo(1L);
        assertThat(result.getReviewableType()).isEqualTo(ReviewableType.TOUR_PACKAGE);
    }

    @Test
    void createReview_bookingNotCompleted_throwsReviewNotEligible() {
        when(bookingService.getBookingById(500L, reviewer))
                .thenReturn(completedBookingFor(1L, 100L, BookingStatus.CONFIRMED));

        assertThatThrownBy(() -> service.createReview(packageReview(), reviewer))
                .isInstanceOf(ReviewNotEligibleException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_bookingBelongsToAnotherTourist_throwsAccessDenied() {
        when(bookingService.getBookingById(500L, reviewer))
                .thenReturn(completedBookingFor(2L, 100L, BookingStatus.COMPLETED));

        assertThatThrownBy(() -> service.createReview(packageReview(), reviewer))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createReview_bookingIsForADifferentPackage_throwsReviewableMismatch() {
        when(bookingService.getBookingById(500L, reviewer))
                .thenReturn(completedBookingFor(1L, 999L, BookingStatus.COMPLETED));

        assertThatThrownBy(() -> service.createReview(packageReview(), reviewer))
                .isInstanceOf(ReviewableMismatchException.class);
    }

    @Test
    void createReview_alreadyReviewed_throwsDuplicateReview() {
        when(bookingService.getBookingById(500L, reviewer))
                .thenReturn(completedBookingFor(1L, 100L, BookingStatus.COMPLETED));
        when(reviewRepository.existsByReviewableTypeAndSourceBookingId(ReviewableType.TOUR_PACKAGE, 500L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createReview(packageReview(), reviewer))
                .isInstanceOf(DuplicateReviewException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_completedOwnedReservation_savesAccommodationReview() {
        ReviewRequestDto request = new ReviewRequestDto(ReviewableType.ACCOMMODATION, 50L, 600L, 4, "Nice stay");
        RoomReservationResponseDto reservation = RoomReservationResponseDto.builder()
                .id(600L).touristId(1L).status(RoomReservationStatus.COMPLETED)
                .room(RoomSummaryDto.builder().id(60L).accommodationId(50L).build())
                .build();
        when(roomReservationService.getReservationById(600L, reviewer)).thenReturn(reservation);
        when(reviewRepository.existsByReviewableTypeAndSourceBookingId(ReviewableType.ACCOMMODATION, 600L))
                .thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponseDto result = service.createReview(request, reviewer);

        assertThat(result.getReviewableType()).isEqualTo(ReviewableType.ACCOMMODATION);
        assertThat(result.getReviewableId()).isEqualTo(50L);
    }

    // ------------------------------------------------------------------
    // updateReview / deleteReview — reviewer + admin checks
    // ------------------------------------------------------------------

    @Test
    void updateReview_byReviewer_updatesRatingAndComment() {
        Review review = persistedReview(reviewer);
        when(reviewRepository.findById(7L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewRequestDto request = new ReviewRequestDto(ReviewableType.TOUR_PACKAGE, 100L, 500L, 2, "Changed my mind");
        ReviewResponseDto result = service.updateReview(7L, request, reviewer);

        assertThat(result.getRating()).isEqualTo(2);
        assertThat(result.getComment()).isEqualTo("Changed my mind");
    }

    @Test
    void updateReview_byNonReviewer_throwsAccessDenied() {
        when(reviewRepository.findById(7L)).thenReturn(Optional.of(persistedReview(reviewer)));

        ReviewRequestDto request = new ReviewRequestDto(ReviewableType.TOUR_PACKAGE, 100L, 500L, 1, "Sabotage");

        assertThatThrownBy(() -> service.updateReview(7L, request, stranger))
                .isInstanceOf(AccessDeniedException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void deleteReview_byReviewer_deletes() {
        Review review = persistedReview(reviewer);
        when(reviewRepository.findById(7L)).thenReturn(Optional.of(review));

        service.deleteReview(7L, reviewer);

        verify(reviewRepository).delete(review);
    }

    @Test
    void deleteReview_byAdmin_deletes() {
        Review review = persistedReview(reviewer);
        when(reviewRepository.findById(7L)).thenReturn(Optional.of(review));

        service.deleteReview(7L, admin);

        verify(reviewRepository).delete(review);
    }

    @Test
    void deleteReview_byUnrelatedUser_throwsAccessDenied() {
        when(reviewRepository.findById(7L)).thenReturn(Optional.of(persistedReview(reviewer)));

        assertThatThrownBy(() -> service.deleteReview(7L, stranger))
                .isInstanceOf(AccessDeniedException.class);
        verify(reviewRepository, never()).delete(any());
    }

    @Test
    void deleteReview_notFound_throwsResourceNotFound() {
        when(reviewRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteReview(404L, admin))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // getRatingSummary
    // ------------------------------------------------------------------

    @Test
    void getRatingSummary_averagesRatingsAndCountsReviews() {
        when(reviewRepository.findByReviewableTypeAndReviewableId(eq(ReviewableType.TOUR_PACKAGE), eq(100L)))
                .thenReturn(List.of(
                        Review.builder().rating(5).reviewer(reviewer).build(),
                        Review.builder().rating(4).reviewer(reviewer).build(),
                        Review.builder().rating(4).reviewer(reviewer).build()));

        RatingSummaryDto summary = service.getRatingSummary(ReviewableType.TOUR_PACKAGE, 100L);

        assertThat(summary.getTotalReviews()).isEqualTo(3);
        assertThat(summary.getAverageRating()).isEqualTo(4.3); // (5+4+4)/3 = 4.33 -> rounded to 1dp
    }

    @Test
    void getRatingSummary_noReviews_returnsZeroes() {
        when(reviewRepository.findByReviewableTypeAndReviewableId(any(), any())).thenReturn(List.of());

        RatingSummaryDto summary = service.getRatingSummary(ReviewableType.VEHICLE, 40L);

        assertThat(summary.getTotalReviews()).isZero();
        assertThat(summary.getAverageRating()).isEqualTo(0.0);
    }
}
