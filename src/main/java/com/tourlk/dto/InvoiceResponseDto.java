package com.tourlk.dto;

import com.tourlk.enums.PayableType;
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
public class InvoiceResponseDto {

    private String invoiceNumber;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime issuedAt;
    private String payerName;
    private PayableType payableType;
    private Long payableId;

}
