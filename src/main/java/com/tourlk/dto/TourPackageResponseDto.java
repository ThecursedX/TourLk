package com.tourlk.dto;

import com.tourlk.enums.PackageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TourPackageResponseDto {

    private Long id;
    private String title;
    private String description;
    /** Nested so the frontend still gets a displayable destination name + id. */
    private DestinationResponseDto destination;
    private int durationDays;
    private BigDecimal price;
    private int maxCapacity;
    private PackageStatus status;
    private Long createdById;
    private String createdByName;
    private LocalDateTime createdAt;

}
