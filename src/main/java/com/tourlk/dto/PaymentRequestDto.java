package com.tourlk.dto;

import com.tourlk.enums.PayableType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * amount is only used to detect a stale/tampered client (e.g. price
 * changed since the page loaded) — the real charge is always computed
 * server-side from the Booking/RoomReservation itself. See
 * {@code PaymentServiceImpl#createPaymentIntent}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestDto {

    @NotNull(message = "Payable type is required")
    private PayableType payableType;

    @NotNull(message = "Payable id is required")
    private Long payableId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be a positive number")
    private BigDecimal amount;

}
