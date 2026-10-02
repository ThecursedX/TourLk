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
public class PaymentSummaryDto {

    /** Gross amount of every payment that completed (SUCCEEDED, REFUND_PENDING or REFUNDED). */
    private BigDecimal totalCollected;
    /** Sum of refund amounts across REFUNDED payments. */
    private BigDecimal totalRefunded;
    private long pendingCount;
    private long failedCount;

}
