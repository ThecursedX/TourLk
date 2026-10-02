package com.tourlk.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.param.RefundCreateParams;
import com.tourlk.entity.Payment;
import com.tourlk.entity.User;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.PayableType;
import com.tourlk.enums.PaymentStatus;
import com.tourlk.enums.Role;
import com.tourlk.exception.PaymentGatewayException;
import com.tourlk.repo.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RefundGateway} — the shared Stripe-refund call used
 * by both admin-triggered ({@code PaymentServiceImpl}) and policy-driven
 * ({@code BookingServiceImpl}) refunds.
 */
@ExtendWith(MockitoExtension.class)
class RefundGatewayTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private RefundGateway refundGateway;

    private Payment payment;

    @BeforeEach
    void setUp() {
        User payer = User.builder().id(1L).name("Tess").email("tess@example.com").role(Role.TOURIST).build();
        payment = Payment.builder()
                .id(55L).payer(payer).payableType(PayableType.BOOKING).payableId(10L)
                .amount(new BigDecimal("200.00")).currency("usd").stripePaymentIntentId("pi_1")
                .status(PaymentStatus.SUCCEEDED)
                .build();
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void refund_fullAmount_omitsAmountFromStripeParams() {
        ArgumentCaptor<RefundCreateParams> paramsCaptor = ArgumentCaptor.forClass(RefundCreateParams.class);
        Payment result;
        try (MockedStatic<Refund> refunds = mockStatic(Refund.class)) {
            refunds.when(() -> Refund.create(any(RefundCreateParams.class))).thenReturn(mock(Refund.class));

            result = refundGateway.refund(payment, new BigDecimal("200.00"));

            refunds.verify(() -> Refund.create(paramsCaptor.capture()));
        }

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(result.getRefundAmount()).isEqualByComparingTo("200.00");
        assertThat(paramsCaptor.getValue().getAmount()).isNull();
        verify(notificationService, org.mockito.Mockito.times(2)).notify(any(), eq(NotificationType.PAYMENT_REFUNDED), any(), any(), any());
    }

    @Test
    void refund_partialAmount_passesAmountInMinorUnitsToStripe() {
        ArgumentCaptor<RefundCreateParams> paramsCaptor = ArgumentCaptor.forClass(RefundCreateParams.class);
        Payment result;
        try (MockedStatic<Refund> refunds = mockStatic(Refund.class)) {
            refunds.when(() -> Refund.create(any(RefundCreateParams.class))).thenReturn(mock(Refund.class));

            result = refundGateway.refund(payment, new BigDecimal("100.00"));

            refunds.verify(() -> Refund.create(paramsCaptor.capture()));
        }

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(result.getRefundAmount()).isEqualByComparingTo("100.00");
        assertThat(paramsCaptor.getValue().getAmount()).isEqualTo(10_000L);
    }

    @Test
    void refund_stripeThrows_wrapsInPaymentGatewayException() {
        try (MockedStatic<Refund> refunds = mockStatic(Refund.class)) {
            refunds.when(() -> Refund.create(any(RefundCreateParams.class)))
                    .thenThrow(mock(StripeException.class));

            assertThatThrownBy(() -> refundGateway.refund(payment, new BigDecimal("200.00")))
                    .isInstanceOf(PaymentGatewayException.class);
        }
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
    }

    @Test
    void cancelUncompletedPayment_marksCancelled() {
        payment.setStatus(PaymentStatus.PENDING);

        Payment result = refundGateway.cancelUncompletedPayment(payment);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

}
