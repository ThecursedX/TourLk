package com.tourlk.dto;

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
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomRequestDto {

    @NotBlank(message = "Room type is required")
    @Size(max = 150, message = "Room type must be at most 150 characters")
    private String roomType;

    @NotNull(message = "Price per night is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price per night must be greater than 0")
    private BigDecimal pricePerNight;

    @NotNull(message = "Total rooms is required")
    @Positive(message = "Total rooms must be a positive number")
    private Integer totalRooms;

    @NotNull(message = "Max occupancy is required")
    @Positive(message = "Max occupancy must be a positive number")
    private Integer maxOccupancy;

    @Size(max = 30, message = "At most 30 facilities")
    private List<@NotBlank(message = "Facility must not be blank")
                 @Size(max = 100, message = "Facility must be at most 100 characters") String> facilities;

    @Size(max = 10, message = "At most 10 images are allowed")
    private List<@NotBlank(message = "Image URL must not be blank")
                 @Size(max = 1000, message = "Image URL must be at most 1000 characters") String> imageUrls;

}
