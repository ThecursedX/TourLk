package com.tourlk.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.param.RefundCreateParams;
import com.tourlk.entity.Payment;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.PaymentStatus;
import com.tourlk.exception.PaymentGatewayException;
import com.tourlk.repo.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The actual Stripe-refund call, shared by {@code PaymentServiceImpl}
 * (admin-triggered, via {@code PUT /api/payments/{id}/refund}) and
 * {@code BookingServiceImpl} (policy-driven, on cancel/reject) so both
 * paths go through the exact same gateway code and land in the same
 * REFUND_PENDING -&gt; REFUNDED states.
 */
@Component
@RequiredArgsConstructor
public class RefundGateway {

    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;

    /**
     * Refunds {@code refundAmount} of a SUCCEEDED {@code payment} via
     * Stripe. A partial amount (less than the full {@code payment.amount})
     * is passed explicitly to Stripe; an amount equal to the full payment
     * omits it, requesting a full refund.
     */
    @Transactional
    public Payment refund(Payment payment, BigDecimal refundAmount) {
        payment.setStatus(PaymentStatus.REFUND_PENDING);
        payment.setRefundAmount(refundAmount);
        paymentRepository.save(payment);

        try {
            RefundCreateParams.Builder params = RefundCreateParams.builder()
                    .setPaymentIntent(payment.getStripePaymentIntentId());
            if (refundAmount.compareTo(payment.getAmount()) < 0) {
                params.setAmount(toMinorUnits(refundAmount));
            }
            Refund.create(params.build());
        } catch (StripeException e) {
            throw new PaymentGatewayException("Could not process refund: " + e.getMessage(), e);
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment = paymentRepository.save(payment);

        notificationService.notify(payment.getPayer(), NotificationType.PAYMENT_REFUNDED,
                "Payment refunded", "Your payment of " + refundAmount + " " + payment.getCurrency()
                        + " has been refunded", "/payments/mine");

        return payment;
    }

    /** Marks a payment that never completed (still PENDING) as CANCELLED — there is nothing to refund. */
    @Transactional
    public Payment cancelUncompletedPayment(Payment payment) {
        payment.setStatus(PaymentStatus.CANCELLED);
        return paymentRepository.save(payment);
    }

    /** usd has 2 decimal places — Stripe wants amounts in the smallest unit (cents). */
    private long toMinorUnits(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }
}
