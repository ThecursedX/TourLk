package com.tourlk.dto;

import com.tourlk.enums.Province;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class DestinationRequestDto {

    /** Uniqueness is validated in the service, not by an annotation. */
    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    @NotNull(message = "Province is required")
    private Province province;

    /** Must be one of the province's districts; checked in the service. */
    @NotBlank(message = "District is required")
    @Size(max = 100, message = "District must be at most 100 characters")
    private String district;

    @NotBlank(message = "Category is required")
    @Size(max = 100, message = "Category must be at most 100 characters")
    private String category;

    @Size(max = 200, message = "Best time to visit must be at most 200 characters")
    private String bestTimeToVisit;

    /** Optional; blank entries are dropped and the rest checked in the service. */
    private List<@Size(max = 1000, message = "Image URL must be at most 1000 characters") String> imageUrls;

    @Size(max = 300, message = "Opening hours must be at most 300 characters")
    private String openingHours;

    @DecimalMin(value = "0.0", message = "Entry fee cannot be negative")
    @DecimalMax(value = "99999999.99", message = "Entry fee is too large")
    private BigDecimal entryFee;

    @Size(max = 4000, message = "Visitor rules must be at most 4000 characters")
    private String visitorRules;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private Double longitude;

    /** On create only: save as DRAFT instead of publishing straight away. */
    private Boolean saveAsDraft;

}
