package com.tourlk.dto;

import com.tourlk.enums.DestinationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DestinationResponseDto {

    private Long id;
    private String name;
    private String region;
    private String description;
    private DestinationStatus status;

    /**
     * Number of ACTIVE tour packages / accommodations currently at this
     * destination. Populated for admin and detail views; may be null on
     * lightweight list responses where the counts aren't needed.
     */
    private Long activePackageCount;
    private Long activeAccommodationCount;

    private LocalDateTime createdAt;

}
