package com.tourlk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Minimal snapshot of a package, nested inside {@link BookingResponseDto}
 * so booking screens don't need a second round trip to /api/packages/{id}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingPackageSummaryDto {

    private Long id;
    private String title;
    private String destination;
    private BigDecimal price;

}
