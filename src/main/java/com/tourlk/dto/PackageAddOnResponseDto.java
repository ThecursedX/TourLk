package com.tourlk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An attached add-on: {@code room} or {@code vehicle} is set, never both. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PackageAddOnResponseDto {

    private Long id;
    private String note;
    private AddOnRoomDto room;
    private AddOnVehicleDto vehicle;

}
