package com.tourlk.dto;

import com.tourlk.enums.DestinationStatus;
import com.tourlk.enums.Province;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DestinationResponseDto {

    private Long id;
    private String name;

    /** Legacy free-text region; mirrors the province's display name. */
    private String region;

    private String description;
    private Province province;
    private String district;
    private String category;
    private String bestTimeToVisit;

    /**
     * Populated on the full destination responses (browse/detail/admin);
     * {@code null} on the lightweight summary nested inside tour package
     * and accommodation responses.
     */
    private List<String> imageUrls;

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
