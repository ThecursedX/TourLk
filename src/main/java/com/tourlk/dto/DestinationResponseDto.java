package com.tourlk.dto;

import com.tourlk.enums.DestinationStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class DestinationResponseDto {
    private Long id;
    private String name;
    private String region;

    // අලුතින් එකතු කරපු ෆීල්ඩ්ස්
    private String district;
    private String category;
    private String bestTimeToVisit;
    private String imageUrl;

    private String description;
    private DestinationStatus status;
    private LocalDateTime createdAt;

    private long activePackageCount;
    private long activeAccommodationCount;
}