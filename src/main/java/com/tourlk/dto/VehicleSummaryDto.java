package com.tourlk.dto;

import com.tourlk.enums.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Minimal vehicle snapshot, nested inside {@link VehicleHireResponseDto}
 * so hire screens don't need a second round trip to /api/vehicles/{id}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleSummaryDto {

    private Long id;
    private VehicleType vehicleType;
    private String make;
    private String model;
    private String registrationNumber;
    private BigDecimal pricePerDay;

}
