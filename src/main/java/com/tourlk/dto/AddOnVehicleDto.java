package com.tourlk.dto;

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
public class AddOnVehicleDto {

    private Long id;
    private String make;
    private String model;
    private VehicleType vehicleType;
    private int seatingCapacity;
    private BigDecimal pricePerDay;

}
