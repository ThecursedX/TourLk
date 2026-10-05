package com.tourlk.dto;

import com.tourlk.enums.AccommodationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Minimal room + accommodation snapshot, nested inside
 * {@link RoomReservationResponseDto} so reservation screens don't need a
 * second round trip to /api/accommodations/{id}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomSummaryDto {

    private Long id;
    private String roomType;
    private BigDecimal pricePerNight;
    private Long accommodationId;
    private String accommodationName;
    private String accommodationLocation;
    /** Lets payment refuse a reservation whose property is no longer open. */
    private AccommodationStatus accommodationStatus;
    /** Room's first image, else the property's first image, else null. */
    private String coverImageUrl;

}
