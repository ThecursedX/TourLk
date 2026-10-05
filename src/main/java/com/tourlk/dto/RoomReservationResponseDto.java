package com.tourlk.dto;

import com.tourlk.enums.RoomReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomReservationResponseDto {

    private Long id;
    private RoomSummaryDto room;
    private Long touristId;
    private String touristName;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private int numberOfRooms;
    /** Null for reservations created before occupancy was enforced. */
    private Integer numberOfGuests;
    /** Frozen at creation; falls back to the live room price for legacy rows. */
    private BigDecimal totalPrice;
    private RoomReservationStatus status;
    /** Set when this is an add-on of a package booking — the booking pays for it. */
    private Long bookingId;
    private String packageTitle;
    private LocalDateTime createdAt;

}
