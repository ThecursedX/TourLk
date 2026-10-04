package com.tourlk.dto;

import com.tourlk.enums.VehicleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
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
public class VehicleRequestDto {

    @NotNull(message = "Vehicle type is required")
    private VehicleType vehicleType;

    @NotBlank(message = "Make is required")
    @Size(max = 100, message = "Make must be at most 100 characters")
    private String make;

    @NotBlank(message = "Model is required")
    @Size(max = 100, message = "Model must be at most 100 characters")
    private String model;

    @NotBlank(message = "Registration number is required")
    @Size(max = 30, message = "Registration number must be at most 30 characters")
    private String registrationNumber;

    @NotNull(message = "Seating capacity is required")
    @Positive(message = "Seating capacity must be a positive number")
    private Integer seatingCapacity;

    @NotNull(message = "Price per day is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price per day must be greater than 0")
    private BigDecimal pricePerDay;

    private Boolean airConditioned;

    @Size(max = 30, message = "At most 30 facilities")
    private List<@NotBlank(message = "Facility must not be blank")
                 @Size(max = 100, message = "Facility must be at most 100 characters") String> facilities;

    @Size(max = 10, message = "At most 10 images are allowed")
    private List<@NotBlank(message = "Image URL must not be blank")
                 @Size(max = 1000, message = "Image URL must be at most 1000 characters") String> imageUrls;

    @Size(max = 100, message = "Driver name must be at most 100 characters")
    private String driverName;

    @Size(max = 30, message = "Driver phone must be at most 30 characters")
    private String driverPhone;

    private LocalDate insuranceExpiry;

    private LocalDate lastMaintenanceDate;

    private LocalDate nextMaintenanceDate;

}
