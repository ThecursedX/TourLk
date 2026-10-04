package com.tourlk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** What cancelling a booking today would refund, per the configured cancellation policy. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancellationPreviewResponseDto {

    private int refundPercent;
    private BigDecimal refundAmount;
    private String ruleText;
    /** False if there's no SUCCEEDED payment to refund (e.g. the booking was never paid). */
    private boolean hasPayment;

}
