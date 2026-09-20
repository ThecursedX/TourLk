package com.tourlk.dto;

import com.tourlk.enums.Province;
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

}
