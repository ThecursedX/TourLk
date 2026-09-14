package com.tourlk.service;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.tourlk.dto.BookingResponseDto;
import com.tourlk.dto.InvoiceResponseDto;
import com.tourlk.dto.PaymentIntentResponseDto;
import com.tourlk.dto.PaymentRequestDto;
import com.tourlk.dto.PaymentResponseDto;
import com.tourlk.dto.RoomReservationResponseDto;
import com.tourlk.dto.VehicleHireResponseDto;
import com.tourlk.entity.Invoice;
import com.tourlk.entity.Payment;
import com.tourlk.entity.User;
import com.tourlk.enums.BookingStatus;
import com.tourlk.enums.PayableType;
import com.tourlk.enums.PaymentStatus;
import com.tourlk.enums.Role;
import com.tourlk.enums.RoomReservationStatus;
import com.tourlk.enums.VehicleHireStatus;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.PaymentAmountMismatchException;
import com.tourlk.exception.PaymentGatewayException;
import com.tourlk.exception.PaymentRequiredException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.InvoiceRepository;
import com.tourlk.repo.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Charges are always computed server-side from the real Booking /
 * RoomReservation (never trusting {@code PaymentRequestDto.amount} for
 * the actual charge — see {@code resolveBookingAmount} /
 * {@code resolveReservationAmount}), and the webhook handlers are only
 * ever reached after {@code PaymentController} has verified the Stripe
 * signature — this class never has to (and must not) trust an
 * unverified caller telling it a payment succeeded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String CURRENCY = "usd";

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final BookingService bookingService;
    private final RoomReservationService roomReservationService;
    // Transport module touchpoint (see VehicleHireServiceImpl for the hire side).
    private final VehicleHireService vehicleHireService;

    @Override
    @Transactional
    public PaymentIntentResponseDto createPaymentIntent(PaymentRequestDto request, User currentUser) {
        BigDecimal realAmount = switch (request.getPayableType()) {
            case BOOKING -> resolveBookingAmount(request.getPayableId(), currentUser);
            case ROOM_RESERVATION -> resolveReservationAmount(request.getPayableId(), currentUser);
            case VEHICLE_HIRE -> resolveVehicleHireAmount(request.getPayableId(), currentUser);
        };

        if (realAmount.compareTo(request.getAmount()) != 0) {
            throw new PaymentAmountMismatchException(
                    "The amount for this payment has changed (expected " + realAmount
                            + "). Please refresh and try again.");
        }

        PaymentIntent intent =
                createStripePaymentIntent(realAmount, request.getPayableType(), request.getPayableId());

        Payment payment = Payment.builder()
                .payer(currentUser)
                .payableType(request.getPayableType())
                .payableId(request.getPayableId())
                .amount(realAmount)
                .currency(CURRENCY)
                .stripePaymentIntentId(intent.getId())
                .status(PaymentStatus.PENDING)
                .build();
        payment = paymentRepository.save(payment);

        return PaymentIntentResponseDto.builder()
                .clientSecret(intent.getClientSecret())
                .paymentId(payment.getId())
                .build();
    }

    private BigDecimal resolveBookingAmount(Long bookingId, User currentUser) {
        BookingResponseDto booking = bookingService.getBookingById(bookingId, currentUser);

        if (!booking.getTouristId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Only the tourist who made this booking can pay for it");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new PaymentRequiredException(
                    "This booking is not awaiting payment (status: " + booking.getStatus() + ")");
        }

        return booking.getTourPackage().getPrice().multiply(BigDecimal.valueOf(booking.getNumberOfTravelers()));
    }

    private BigDecimal resolveReservationAmount(Long reservationId, User currentUser) {
        RoomReservationResponseDto reservation =
                roomReservationService.getReservationById(reservationId, currentUser);

        if (!reservation.getTouristId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Only the tourist who made this reservation can pay for it");
        }
        if (reservation.getStatus() != RoomReservationStatus.PENDING) {
            throw new PaymentRequiredException(
                    "This reservation is not awaiting payment (status: " + reservation.getStatus() + ")");
        }

        long nights = ChronoUnit.DAYS.between(reservation.getCheckInDate(), reservation.getCheckOutDate());
        return reservation.getRoom().getPricePerNight()
                .multiply(BigDecimal.valueOf(nights))
                .multiply(BigDecimal.valueOf(reservation.getNumberOfRooms()));
    }

    // --- Transport module touchpoint: VehicleHire pays the same way Booking/RoomReservation do. ---
    private BigDecimal resolveVehicleHireAmount(Long hireId, User currentUser) {
        VehicleHireResponseDto hire = vehicleHireService.getHireById(hireId, currentUser);

        if (!hire.getTouristId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Only the tourist who made this hire can pay for it");
        }
        if (hire.getStatus() != VehicleHireStatus.PENDING) {
            throw new PaymentRequiredException(
                    "This hire is not awaiting payment (status: " + hire.getStatus() + ")");
        }

        // totalPrice was already computed and frozen on the hire at creation
        // time (see VehicleHireServiceImpl#createHire) — reuse it rather
        // than recomputing from the vehicle's current pricePerDay, so what
        // the tourist is charged matches what they were quoted.
        return hire.getTotalPrice();
    }

    private PaymentIntent createStripePaymentIntent(BigDecimal amount, PayableType payableType, Long payableId) {
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(toMinorUnits(amount))
                    .setCurrency(CURRENCY)
                    .putMetadata("payableType", payableType.name())
                    .putMetadata("payableId", String.valueOf(payableId))
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build())
                    .build();
            return PaymentIntent.create(params);
        } catch (StripeException e) {
            throw new PaymentGatewayException("Could not start payment: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void handlePaymentSucceeded(String stripePaymentIntentId) {
        Payment payment = paymentRepository.findByStripePaymentIntentId(stripePaymentIntentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No payment found for PaymentIntent " + stripePaymentIntentId));

        // Webhooks can be delivered more than once (Stripe retries on any
        // non-2xx response, and duplicates do happen even without that) —
        // make this idempotent rather than double-confirming the
        // booking/reservation or generating a second invoice.
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        payment.setStatus(PaymentStatus.SUCCEEDED);
        paymentRepository.save(payment);

        // The charge succeeded — that's a fact, and the Payment row above
        // already reflects it. If confirming the underlying booking fails
        // (e.g. it was cancelled in the meantime), we still ACK the
        // webhook rather than making Stripe retry a business conflict
        // forever; log loudly so it gets a human's attention.
        try {
            switch (payment.getPayableType()) {
                case BOOKING -> bookingService.confirmBooking(payment.getPayableId());
                case ROOM_RESERVATION ->
                        roomReservationService.confirmReservationAfterPayment(payment.getPayableId());
                // Transport module touchpoint.
                case VEHICLE_HIRE -> vehicleHireService.confirmHireAfterPayment(payment.getPayableId());
            }
            generateInvoice(payment);
        } catch (RuntimeException ex) {
            log.error("Payment {} succeeded but confirming {} {} failed — needs manual review",
                    payment.getId(), payment.getPayableType(), payment.getPayableId(), ex);
        }
    }

    @Override
    @Transactional
    public void handlePaymentFailed(String stripePaymentIntentId) {
        Payment payment = paymentRepository.findByStripePaymentIntentId(stripePaymentIntentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No payment found for PaymentIntent " + stripePaymentIntentId));

        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
        }
    }

    @Override
    @Transactional
    public PaymentResponseDto refundPayment(Long paymentId) {
        Payment payment = getEntity(paymentId);

        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new InvalidStatusTransitionException(
                    "Only SUCCEEDED payments can be refunded, but this payment is " + payment.getStatus());
        }

        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(payment.getStripePaymentIntentId())
                    .build();
            Refund.create(params);
        } catch (StripeException e) {
            throw new PaymentGatewayException("Could not process refund: " + e.getMessage(), e);
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        return toResponse(paymentRepository.save(payment));
    }

    @Override
    public List<PaymentResponseDto> getPaymentsByUser(Long userId) {
        return paymentRepository.findByPayerId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<PaymentResponseDto> getAllPayments() {
        return paymentRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public PaymentResponseDto getPaymentById(Long id, User currentUser) {
        Payment payment = getEntity(id);
        assertPayerOrAdmin(payment, currentUser);
        return toResponse(payment);
    }

    @Override
    public InvoiceResponseDto getInvoiceByPaymentId(Long paymentId, User currentUser) {
        Payment payment = getEntity(paymentId);
        assertPayerOrAdmin(payment, currentUser);

        Invoice invoice = invoiceRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No invoice has been generated for payment " + paymentId));

        return InvoiceResponseDto.builder()
                .invoiceNumber(invoice.getInvoiceNumber())
                .amount(invoice.getAmount())
                .currency(invoice.getCurrency())
                .issuedAt(invoice.getIssuedAt())
                .payerName(payment.getPayer().getName())
                .payableType(payment.getPayableType())
                .payableId(payment.getPayableId())
                .build();
    }

    /**
     * Payment.id is a DB-assigned, monotonically increasing, unique
     * sequence — reused here as the invoice sequence so numbering can't
     * collide under concurrent webhook deliveries without needing a
     * separate counter (and its own locking).
     */
    private void generateInvoice(Payment payment) {
        String invoiceNumber = "INV-" + LocalDateTime.now().getYear() + "-" + String.format("%06d", payment.getId());

        Invoice invoice = Invoice.builder()
                .payment(payment)
                .invoiceNumber(invoiceNumber)
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .issuedAt(LocalDateTime.now())
                .build();

        invoiceRepository.save(invoice);
    }

    /** usd has 2 decimal places — Stripe wants amounts in the smallest unit (cents). */
    private long toMinorUnits(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    private Payment getEntity(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
    }

    private void assertPayerOrAdmin(Payment payment, User currentUser) {
        boolean isPayer = payment.getPayer().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isPayer && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to view this payment");
        }
    }

    private PaymentResponseDto toResponse(Payment payment) {
        return PaymentResponseDto.builder()
                .id(payment.getId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .payableType(payment.getPayableType())
                .payableId(payment.getPayableId())
                .createdAt(payment.getCreatedAt())
                .build();
    }

}
