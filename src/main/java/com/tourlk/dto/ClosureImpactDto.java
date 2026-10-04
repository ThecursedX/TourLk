package com.tourlk.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Preview for the admin: how many active bookings a closure would cancel and fully refund. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClosureImpactDto {

    private int affectedBookings;

}
