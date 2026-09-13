package com.tourlk.dto;

import com.tourlk.enums.VehicleHireStatus;
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
public class VehicleHireResponseDto {

    private Long id;
    private VehicleSummaryDto vehicle;
    private Long touristId;
    private String touristName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String pickupLocation;
    private String notes;
    private BigDecimal totalPrice;
    private VehicleHireStatus status;
    private LocalDateTime createdAt;

}
