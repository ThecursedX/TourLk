package com.tourlk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** What closing a destination did to the existing bookings that fall inside the closure window. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClosureSummaryDto {

    /** Bookings cancelled (each in its own transaction). */
    private int cancelledBookings;

    /** Of those, how many had a SUCCEEDED payment that was refunded in full. */
    private int refundedCount;

    /** Bookings that could not be cancelled/refunded (left untouched); see {@code failedBookingIds}. */
    private int failedRefunds;

    private List<Long> failedBookingIds;

}
