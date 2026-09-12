package com.tourlk.repo;

import com.tourlk.entity.Review;
import com.tourlk.enums.ReviewableType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByReviewableTypeAndReviewableId(ReviewableType reviewableType, Long reviewableId);

    /**
     * Scoped by reviewableType as well as sourceBookingId — Booking,
     * RoomReservation and VehicleHire ids come from independent sequences
     * and can collide, so sourceBookingId alone can't disambiguate which
     * source table it refers to. See ReviewServiceImpl#createReview.
     */
    boolean existsByReviewableTypeAndSourceBookingId(ReviewableType reviewableType, Long sourceBookingId);

    List<Review> findByReviewerId(Long reviewerId);

}
