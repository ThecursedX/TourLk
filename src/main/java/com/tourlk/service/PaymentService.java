package com.tourlk.service;

import com.tourlk.dto.InvoiceResponseDto;
import com.tourlk.dto.PaymentIntentResponseDto;
import com.tourlk.dto.PaymentRequestDto;
import com.tourlk.dto.PaymentResponseDto;
import com.tourlk.entity.User;

import java.util.List;

public interface PaymentService {

    PaymentIntentResponseDto createPaymentIntent(PaymentRequestDto request, User currentUser);

    /** Called only by the (signature-verified) Stripe webhook handler — see PaymentController. */
    void handlePaymentSucceeded(String stripePaymentIntentId);

    /** Called only by the (signature-verified) Stripe webhook handler — see PaymentController. */
    void handlePaymentFailed(String stripePaymentIntentId);

    PaymentResponseDto refundPayment(Long paymentId);

    List<PaymentResponseDto> getPaymentsByUser(Long userId);

    List<PaymentResponseDto> getAllPayments();

    PaymentResponseDto getPaymentById(Long id, User currentUser);

    InvoiceResponseDto getInvoiceByPaymentId(Long paymentId, User currentUser);

}
