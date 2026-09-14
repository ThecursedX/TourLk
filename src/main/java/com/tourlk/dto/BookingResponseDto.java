package com.tourlk.dto;

import com.tourlk.enums.BookingStatus;
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
public class BookingResponseDto {

    private Long id;
    private BookingPackageSummaryDto tourPackage;
    private Long touristId;
    private String touristName;
    private LocalDate travelDate;
    private int numberOfTravelers;
    private String specialRequests;
    private BookingStatus status;
    private LocalDate previousTravelDate;
    private LocalDate requestedTravelDate;
    private LocalDateTime createdAt;

}
