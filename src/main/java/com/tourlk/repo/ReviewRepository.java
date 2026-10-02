package com.tourlk.repo;

import com.tourlk.entity.Review;
import com.tourlk.enums.ReviewStatus;
import com.tourlk.enums.ReviewableType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByReviewableTypeAndReviewableId(ReviewableType reviewableType, Long reviewableId);

    /** Public-facing review lists / rating averages exclude {@link ReviewStatus#HIDDEN_FROM_PUBLIC}. */
    List<Review> findByReviewableTypeAndReviewableIdAndStatusNotIn(
            ReviewableType reviewableType, Long reviewableId, Collection<ReviewStatus> excludedStatuses);

    /** Used by guide review stats to aggregate across every package the guide owns. */
    List<Review> findByReviewableTypeAndReviewableIdInAndStatusNotIn(
            ReviewableType reviewableType, Collection<Long> reviewableIds, Collection<ReviewStatus> excludedStatuses);

    /**
     * Scoped by reviewableType as well as sourceBookingId — Booking,
     * RoomReservation and VehicleHire ids come from independent sequences
     * and can collide, so sourceBookingId alone can't disambiguate which
     * source table it refers to. See ReviewServiceImpl#createReview.
     */
    boolean existsByReviewableTypeAndSourceBookingId(ReviewableType reviewableType, Long sourceBookingId);

    /**
     * Same as above, but ignores DELETED rows — a soft-deleted review no
     * longer blocks the tourist from leaving a fresh one for the same
     * experience. HIDDEN rows still count: hiding isn't removal.
     */
    boolean existsByReviewableTypeAndSourceBookingIdAndStatusNot(
            ReviewableType reviewableType, Long sourceBookingId, ReviewStatus excludedStatus);

    List<Review> findByReviewerId(Long reviewerId);

    /**
     * Average rating + review count per reviewable, for many reviewables in
     * one grouped query (e.g. a whole package listing). Reviewables with no
     * reviews are simply absent from the result. Excludes the same statuses
     * as the public single-item lookups, so list and detail pages agree.
     */
    @Query("SELECT r.reviewableId AS reviewableId, AVG(r.rating) AS averageRating, COUNT(r) AS reviewCount "
            + "FROM Review r WHERE r.reviewableType = :type AND r.reviewableId IN :ids "
            + "AND r.status NOT IN :excludedStatuses GROUP BY r.reviewableId")
    List<RatingAggregate> aggregateRatings(@Param("type") ReviewableType type, @Param("ids") Collection<Long> ids,
                                            @Param("excludedStatuses") Collection<ReviewStatus> excludedStatuses);

    interface RatingAggregate {
        Long getReviewableId();
        Double getAverageRating();
        Long getReviewCount();
    }

}
