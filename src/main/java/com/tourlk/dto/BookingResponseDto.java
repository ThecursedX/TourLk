package com.tourlk.dto;

import com.tourlk.enums.BookingStatus;
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
public class BookingResponseDto {

    private Long id;
    private BookingPackageSummaryDto tourPackage;
    private Long touristId;
    private String touristName;
    private LocalDate travelDate;
    private int numberOfTravelers;
    /** What the tourist pays: fixed at booking time (legacy rows: live package price * travelers). */
    private BigDecimal totalPrice;
    private String specialRequests;
    private BookingStatus status;
    private LocalDate previousTravelDate;
    private LocalDate requestedTravelDate;
    /** Set when status is REJECTED. */
    private String rejectionReason;
    /** True once a SUCCEEDED payment exists for this booking. */
    private boolean paid;
    private LocalDateTime createdAt;

}
