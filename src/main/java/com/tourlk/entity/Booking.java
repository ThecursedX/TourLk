package com.tourlk.entity;

import com.tourlk.enums.BookingStatus;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * A tourist's booking of a tour package for a specific travel date.
 * Goes through the PENDING -&gt; CONFIRMED -&gt; COMPLETED lifecycle (or
 * CANCELLED / RESCHEDULE_REQUESTED -&gt; RESCHEDULED along the way) managed
 * by {@code BookingServiceImpl}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "bookings")
public class Booking extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tourist_id", nullable = false)
    private User tourist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_package_id", nullable = false)
    private TourPackage tourPackage;

    @Column(name = "travel_date", nullable = false)
    private LocalDate travelDate;

    @Column(name = "number_of_travelers", nullable = false)
    private int numberOfTravelers;

    @Column(name = "special_requests", length = 1000)
    private String specialRequests;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING;

    /** Set on approveReschedule, for audit — the travelDate before that reschedule. */
    @Column(name = "previous_travel_date")
    private LocalDate previousTravelDate;

    /** The tourist's proposed new date while status is RESCHEDULE_REQUESTED, awaiting admin decision. */
    @Column(name = "requested_travel_date")
    private LocalDate requestedTravelDate;

    /** The status to revert to if the pending reschedule request is rejected. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_before_reschedule", length = 30)
    private BookingStatus statusBeforeReschedule;

}
