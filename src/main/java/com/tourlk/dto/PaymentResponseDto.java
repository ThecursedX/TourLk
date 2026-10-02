package com.tourlk.dto;

import com.tourlk.enums.PayableType;
import com.tourlk.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponseDto {

    private Long id;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private PayableType payableType;
    private Long payableId;
    /** How much was actually refunded, if any — may be less than {@code amount} under a partial refund. */
    private BigDecimal refundAmount;
    private LocalDateTime createdAt;

}
