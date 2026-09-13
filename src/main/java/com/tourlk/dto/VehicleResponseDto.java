package com.tourlk.dto;

import com.tourlk.enums.VehicleStatus;
import com.tourlk.enums.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleResponseDto {

    private Long id;
    private VehicleType vehicleType;
    private String make;
    private String model;
    private String registrationNumber;
    private int seatingCapacity;
    private BigDecimal pricePerDay;
    private VehicleStatus status;
    private Long driverId;
    private String driverName;

}
