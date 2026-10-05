package com.tourlk.entity;

import com.tourlk.enums.RoomReservationStatus;
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

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A tourist's reservation of a {@link Room} type for a date range.
 * Goes through PENDING -&gt; CONFIRMED -&gt; COMPLETED (or CANCELLED)
 * managed by {@code RoomReservationServiceImpl}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "room_reservations")
public class RoomReservation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tourist_id", nullable = false)
    private User tourist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(name = "number_of_rooms", nullable = false)
    private int numberOfRooms;

    /** Guests staying; nullable because rows created before occupancy was enforced have none. */
    @Column(name = "number_of_guests")
    private Integer numberOfGuests;

    /**
     * Price frozen at creation (pricePerNight x nights x rooms). Nullable so legacy rows survive
     * {@code ddl-auto: update}; readers fall back to the live room price when null.
     */
    @Column(name = "total_price", precision = 10, scale = 2)
    private BigDecimal totalPrice;

    /** Set when this was added as an add-on of a package booking; that booking pays for it. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RoomReservationStatus status = RoomReservationStatus.PENDING;

}
