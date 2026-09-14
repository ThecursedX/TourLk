package com.tourlk.entity;

import com.tourlk.enums.ReviewableType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

}
