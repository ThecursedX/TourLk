package com.tourlk.dto;

import com.tourlk.enums.VehicleStatus;
import com.tourlk.enums.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
    /** The vehicle's driver contact name; falls back to the owner's name. */
    private String driverName;
    private String driverPhone;
    private boolean airConditioned;
    private List<String> facilities;
    private List<String> imageUrls;
    private LocalDate insuranceExpiry;
    private LocalDate lastMaintenanceDate;
    private LocalDate nextMaintenanceDate;

}
