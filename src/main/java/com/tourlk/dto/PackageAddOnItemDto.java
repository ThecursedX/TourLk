package com.tourlk.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One add-on to attach: exactly one of {@code roomId} / {@code vehicleId}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PackageAddOnItemDto {

    private Long roomId;
    private Long vehicleId;

    @Size(max = 300, message = "Note must be at most 300 characters")
    private String note;

}
