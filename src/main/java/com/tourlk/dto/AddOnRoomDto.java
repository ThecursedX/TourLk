package com.tourlk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddOnRoomDto {

    private Long id;
    private String roomType;
    private BigDecimal pricePerNight;
    private int totalRooms;
    private int maxOccupancy;
    private Long accommodationId;
    private String accommodationName;

}
