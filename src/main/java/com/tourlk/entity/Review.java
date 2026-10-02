package com.tourlk.entity;

import com.tourlk.enums.ReviewStatus;
import com.tourlk.enums.ReviewableType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A tourist's review of a TourPackage, Accommodation or Vehicle, left
 * only once the {@code Booking}/{@code RoomReservation}/{@code
 * VehicleHire} that earned it (see {@code sourceBookingId}) is COMPLETED.
 * Goes through {@code ReviewServiceImpl#createReview} for that
 * eligibility check — never inserted directly.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "reviews",
        uniqueConstraints = @UniqueConstraint(columnNames = {"reviewable_type", "source_booking_id"}))
public class Review extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @Enumerated(EnumType.STRING)
    @Column(name = "reviewable_type", nullable = false, length = 20)
    private ReviewableType reviewableType;

    @Column(name = "reviewable_id", nullable = false)
    private Long reviewableId;

    /**
     * The Booking/RoomReservation/VehicleHire.id (interpreted according to
     * reviewableType) that proves this reviewer completed this experience.
     * Booking, RoomReservation and VehicleHire ids come from independent
     * sequences and can collide, so this is only ever queried together
     * with reviewableType (see the unique constraint above and
     * ReviewRepository#existsByReviewableTypeAndSourceBookingId).
     */
    @Column(name = "source_booking_id", nullable = false)
    private Long sourceBookingId;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String comment;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "review_images", joinColumns = @JoinColumn(name = "review_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "image_url", length = 1000)
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    /**
     * Moderation/lifecycle status — replaces the old {@code flagged}
     * boolean. No NOT NULL constraint so {@code ddl-auto: update} can add
     * the column to a table that already has rows; legacy rows are
     * backfilled to PUBLISHED/REPORTED by {@code ReviewStatusBackfillRunner}.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PUBLISHED;

    /** Public reply from the guide who owns the reviewed tour package. */
    @Column(name = "guide_reply", length = 1000)
    private String guideReply;

    @Column(name = "guide_reply_at")
    private LocalDateTime guideReplyAt;

}
