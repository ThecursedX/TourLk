package com.tourlk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomResponseDto {

    private Long id;
    private Long accommodationId;
    private String roomType;
    private BigDecimal pricePerNight;
    private int totalRooms;
    private int maxOccupancy;
    private List<String> facilities;
    private List<String> imageUrls;

}
