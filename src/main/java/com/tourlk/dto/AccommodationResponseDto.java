package com.tourlk.dto;

import com.tourlk.enums.AccommodationStatus;
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
public class AccommodationResponseDto {

    private Long id;
    private String name;
    private String description;
    /** Nested so the frontend still gets a displayable location name + id. */
    private DestinationResponseDto location;
    private Integer starRating;
    private AccommodationStatus status;
    private Long ownerId;
    private String ownerName;
    private List<RoomResponseDto> rooms;
    private LocalDateTime createdAt;

}
