package com.tourlk.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A room add-on a tourist picks while booking a package, and how many of that room type. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddOnRoomSelectionDto {

    @NotNull(message = "Room is required")
    private Long roomId;

    @NotNull(message = "Number of rooms is required")
    @Positive(message = "Number of rooms must be a positive number")
    private Integer numberOfRooms;

}
