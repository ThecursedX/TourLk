package com.tourlk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Whether one add-on of a package is free for the trip dates derived from a travel date.
 * {@code roomId} or {@code vehicleId} identifies it; {@code roomsLeft} is set for rooms only.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddOnAvailabilityDto {

    private Long roomId;
    private Long vehicleId;
    private boolean available;
    private Integer roomsLeft;

}
