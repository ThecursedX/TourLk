package com.tourlk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DestinationRequestDto {

    /** Uniqueness is validated in the service, not by an annotation. */
    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    @NotBlank(message = "Region is required")
    @Size(max = 150, message = "Region must be at most 150 characters")
    private String region;

    @Size(max = 150, message = "District must be at most 150 characters")
    private String district;

    @Size(max = 100, message = "Category must be at most 100 characters")
    private String category;

    @Size(max = 150, message = "Best time to visit must be at most 150 characters")
    private String bestTimeToVisit;

    private String imageUrl;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

}