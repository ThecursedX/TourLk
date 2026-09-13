package com.tourlk.dto;

import com.tourlk.enums.RoomReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private RoomReservationStatus status;
    private LocalDateTime createdAt;

}
