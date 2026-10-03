package com.tourlk.service;

import com.tourlk.dto.BulkDeletePaymentsResultDto;
import com.tourlk.dto.InvoiceResponseDto;
import com.tourlk.dto.PaymentIntentResponseDto;
import com.tourlk.dto.PaymentRequestDto;
import com.tourlk.dto.PaymentResponseDto;
import com.tourlk.entity.User;

import com.tourlk.dto.PaymentSummaryDto;
import com.tourlk.enums.PayableType;
import com.tourlk.enums.PaymentStatus;

import java.time.LocalDate;
import java.util.List;

public interface PaymentService {

    PaymentIntentResponseDto createPaymentIntent(PaymentRequestDto request, User currentUser);

    /** Called only by the (signature-verified) Stripe webhook handler — see PaymentController. */
    void handlePaymentSucceeded(String stripePaymentIntentId);

    /** Called only by the (signature-verified) Stripe webhook handler — see PaymentController. */
    void handlePaymentFailed(String stripePaymentIntentId);

    PaymentResponseDto refundPayment(Long paymentId);

    /**
     * Admin clean-up of a payment record: only PENDING, FAILED, REFUNDED (and never-completed CANCELLED)
     * payments. A PENDING payment's Stripe PaymentIntent is cancelled first and its invoice removed.
     * Never touches the booking / reservation / hire the payment was for.
     */
    void deletePayment(Long paymentId, User admin);

    /** Applies {@link #deletePayment} to each id (max 200), skipping — not failing on — protected rows. */
    BulkDeletePaymentsResultDto deletePayments(List<Long> ids, User admin);

    List<PaymentResponseDto> getPaymentsByUser(Long userId);

    List<PaymentResponseDto> getAllPayments();

    /** Admin table: every filter is optional (null = no restriction); newest first. */
    List<PaymentResponseDto> searchPayments(PaymentStatus status, PayableType payableType, String search,
                                            LocalDate from, LocalDate to);

    PaymentSummaryDto getPaymentSummary();

    PaymentResponseDto getPaymentById(Long id, User currentUser);

    InvoiceResponseDto getInvoiceByPaymentId(Long paymentId, User currentUser);

}
