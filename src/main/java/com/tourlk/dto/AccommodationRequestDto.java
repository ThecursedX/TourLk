package com.tourlk.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccommodationRequestDto {

    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must be at most 200 characters")
    private String name;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Location is required")
    private Long locationId;

    @Min(value = 1, message = "Star rating must be between 1 and 5")
    @Max(value = 5, message = "Star rating must be between 1 and 5")
    private Integer starRating;

    @Size(max = 500, message = "Address must be at most 500 characters")
    private String address;

    @Size(max = 30, message = "At most 30 facilities")
    private List<@NotBlank(message = "Facility must not be blank")
                 @Size(max = 100, message = "Facility must be at most 100 characters") String> facilities;

    private String policies;

    @Size(max = 10, message = "At most 10 images are allowed")
    private List<@NotBlank(message = "Image URL must not be blank")
                 @Size(max = 1000, message = "Image URL must be at most 1000 characters") String> imageUrls;

}
