package com.tourlk.service;

import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import com.tourlk.dto.BookingPackageSummaryDto;
import com.tourlk.dto.BookingResponseDto;
import com.tourlk.dto.PaymentIntentResponseDto;
import com.tourlk.dto.PaymentRequestDto;
import com.tourlk.dto.PaymentResponseDto;
import com.tourlk.dto.RoomReservationResponseDto;
import com.tourlk.dto.RoomSummaryDto;
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
import com.tourlk.exception.PaymentRequiredException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.InvoiceRepository;
import com.tourlk.repo.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentServiceImpl}. All collaborators (repos, the
 * feature services, and the Stripe SDK's static {@code create} calls) are
 * mocked — nothing here touches a database or the real Stripe API.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private BookingService bookingService;
    @Mock
    private RoomReservationService roomReservationService;
    @Mock
    private VehicleHireService vehicleHireService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private RefundGateway refundGateway;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private User tourist;

    @BeforeEach
    void setUp() {
        tourist = User.builder()
                .id(1L)
                .name("Tess Tourist")
                .email("tourist@example.com")
                .role(Role.TOURIST)
                .build();
    }

    // ------------------------------------------------------------------
    // createPaymentIntent — server-side amount recompute per PayableType
    // ------------------------------------------------------------------

    @Nested
    class CreatePaymentIntent {

        @Test
        void createPaymentIntent_booking_recomputesAmountServerSideAndPersistsPendingPayment() {
            // package price 100.00 x 2 travelers => real amount 200.00
            when(bookingService.getBookingById(10L, tourist))
                    .thenReturn(booking(1L, BookingStatus.PENDING, "100.00", 2));
            when(paymentRepository.save(any(Payment.class)))
                    .thenAnswer(inv -> withId(inv.getArgument(0), 77L));

            PaymentIntent intent = stripeIntent("pi_booking", "cs_booking");
            PaymentRequestDto request = new PaymentRequestDto(PayableType.BOOKING, 10L, new BigDecimal("200.00"), null);

            PaymentIntentResponseDto result;
            ArgumentCaptor<PaymentIntentCreateParams> paramsCaptor =
                    ArgumentCaptor.forClass(PaymentIntentCreateParams.class);

            try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
                stripe.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class))).thenReturn(intent);

                result = paymentService.createPaymentIntent(request, tourist);

                stripe.verify(() -> PaymentIntent.create(paramsCaptor.capture()));
            }

            // Stripe was asked for the server-computed amount, in minor units (cents)
            assertThat(paramsCaptor.getValue().getAmount()).isEqualTo(20_000L);

            assertThat(result.getClientSecret()).isEqualTo("cs_booking");
            assertThat(result.getPaymentId()).isEqualTo(77L);

            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(paymentCaptor.capture());
            Payment saved = paymentCaptor.getValue();
            assertThat(saved.getAmount()).isEqualByComparingTo("200.00");
            assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(saved.getPayableType()).isEqualTo(PayableType.BOOKING);
            assertThat(saved.getStripePaymentIntentId()).isEqualTo("pi_booking");
            assertThat(saved.getPayer()).isSameAs(tourist);
        }

        @Test
        void createPaymentIntent_roomReservation_recomputesAmountFromNightsAndRooms() {
            // 50.00/night x 3 nights x 2 rooms => 300.00
            when(roomReservationService.getReservationById(20L, tourist))
                    .thenReturn(reservation(1L, RoomReservationStatus.PENDING, "50.00", 2,
                            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 4)));
            when(paymentRepository.save(any(Payment.class)))
                    .thenAnswer(inv -> withId(inv.getArgument(0), 78L));

            PaymentIntent intent = stripeIntent("pi_res", "cs_res");
            PaymentRequestDto request =
                    new PaymentRequestDto(PayableType.ROOM_RESERVATION, 20L, new BigDecimal("300.00"), null);

            try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
                stripe.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class))).thenReturn(intent);
                paymentService.createPaymentIntent(request, tourist);
            }

            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(paymentCaptor.capture());
            assertThat(paymentCaptor.getValue().getAmount()).isEqualByComparingTo("300.00");
        }

        @Test
        void createPaymentIntent_vehicleHire_usesFrozenTotalPriceFromHire() {
            when(vehicleHireService.getHireById(30L, tourist))
                    .thenReturn(hire(1L, VehicleHireStatus.PENDING, "450.00"));
            when(paymentRepository.save(any(Payment.class)))
                    .thenAnswer(inv -> withId(inv.getArgument(0), 79L));

            PaymentIntent intent = stripeIntent("pi_hire", "cs_hire");
            PaymentRequestDto request =
                    new PaymentRequestDto(PayableType.VEHICLE_HIRE, 30L, new BigDecimal("450.00"), null);

            try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
                stripe.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class))).thenReturn(intent);
                paymentService.createPaymentIntent(request, tourist);
            }

            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(paymentCaptor.capture());
            assertThat(paymentCaptor.getValue().getAmount()).isEqualByComparingTo("450.00");
        }

        @Test
        void createPaymentIntent_bookingAmountMismatch_throwsAndNeverCallsStripeOrPersists() {
            when(bookingService.getBookingById(10L, tourist))
                    .thenReturn(booking(1L, BookingStatus.PENDING, "100.00", 2)); // real 200.00

            PaymentRequestDto request = new PaymentRequestDto(PayableType.BOOKING, 10L, new BigDecimal("150.00"), null);

            try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
                assertThatThrownBy(() -> paymentService.createPaymentIntent(request, tourist))
                        .isInstanceOf(PaymentAmountMismatchException.class);
                stripe.verifyNoInteractions();
            }
            verify(paymentRepository, never()).save(any());
        }

        @Test
        void createPaymentIntent_bookingUsesStoredTotalPrice_notTheCurrentPackagePrice() {
            BookingResponseDto booking = booking(1L, BookingStatus.PENDING, "100.00", 2); // booked at 200.00
            booking.getTourPackage().setPrice(new BigDecimal("175.00")); // package repriced since
            when(bookingService.getBookingById(10L, tourist)).thenReturn(booking);

            PaymentRequestDto request = new PaymentRequestDto(PayableType.BOOKING, 10L, new BigDecimal("350.00"), null);

            try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
                assertThatThrownBy(() -> paymentService.createPaymentIntent(request, tourist))
                        .isInstanceOf(PaymentAmountMismatchException.class)
                        .hasMessageContaining("expected 200.00");
                stripe.verifyNoInteractions();
            }
        }

        @Test
        void createPaymentIntent_reservationAmountMismatch_throws() {
            when(roomReservationService.getReservationById(20L, tourist))
                    .thenReturn(reservation(1L, RoomReservationStatus.PENDING, "50.00", 2,
                            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 4))); // real 300.00

            PaymentRequestDto request =
                    new PaymentRequestDto(PayableType.ROOM_RESERVATION, 20L, new BigDecimal("299.99"), null);

            assertThatThrownBy(() -> paymentService.createPaymentIntent(request, tourist))
                    .isInstanceOf(PaymentAmountMismatchException.class);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        void createPaymentIntent_vehicleHireAmountMismatch_throws() {
            when(vehicleHireService.getHireById(30L, tourist))
                    .thenReturn(hire(1L, VehicleHireStatus.PENDING, "450.00"));

            PaymentRequestDto request =
                    new PaymentRequestDto(PayableType.VEHICLE_HIRE, 30L, new BigDecimal("400.00"), null);

            assertThatThrownBy(() -> paymentService.createPaymentIntent(request, tourist))
                    .isInstanceOf(PaymentAmountMismatchException.class);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        void createPaymentIntent_payingForSomeoneElsesBooking_throwsAccessDenied() {
            when(bookingService.getBookingById(10L, tourist))
                    .thenReturn(booking(999L, BookingStatus.PENDING, "100.00", 2));

            PaymentRequestDto request = new PaymentRequestDto(PayableType.BOOKING, 10L, new BigDecimal("200.00"), null);

            assertThatThrownBy(() -> paymentService.createPaymentIntent(request, tourist))
                    .isInstanceOf(AccessDeniedException.class);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        void createPaymentIntent_payingForSomeoneElsesReservation_throwsAccessDenied() {
            when(roomReservationService.getReservationById(20L, tourist))
                    .thenReturn(reservation(999L, RoomReservationStatus.PENDING, "50.00", 2,
                            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 4)));

            PaymentRequestDto request =
                    new PaymentRequestDto(PayableType.ROOM_RESERVATION, 20L, new BigDecimal("300.00"), null);

            assertThatThrownBy(() -> paymentService.createPaymentIntent(request, tourist))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void createPaymentIntent_payingForSomeoneElsesVehicleHire_throwsAccessDenied() {
            when(vehicleHireService.getHireById(30L, tourist))
                    .thenReturn(hire(999L, VehicleHireStatus.PENDING, "450.00"));

            PaymentRequestDto request =
                    new PaymentRequestDto(PayableType.VEHICLE_HIRE, 30L, new BigDecimal("450.00"), null);

            assertThatThrownBy(() -> paymentService.createPaymentIntent(request, tourist))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void createPaymentIntent_bookingCancelled_throwsPaymentRequired() {
            when(bookingService.getBookingById(10L, tourist))
                    .thenReturn(booking(1L, BookingStatus.CANCELLED, "100.00", 2));

            PaymentRequestDto request = new PaymentRequestDto(PayableType.BOOKING, 10L, new BigDecimal("200.00"), null);

            assertThatThrownBy(() -> paymentService.createPaymentIntent(request, tourist))
                    .isInstanceOf(PaymentRequiredException.class);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        void createPaymentIntent_reservationNotPending_throwsPaymentRequired() {
            when(roomReservationService.getReservationById(20L, tourist))
                    .thenReturn(reservation(1L, RoomReservationStatus.CONFIRMED, "50.00", 2,
                            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 4)));

            PaymentRequestDto request =
                    new PaymentRequestDto(PayableType.ROOM_RESERVATION, 20L, new BigDecimal("300.00"), null);

            assertThatThrownBy(() -> paymentService.createPaymentIntent(request, tourist))
                    .isInstanceOf(PaymentRequiredException.class);
        }

        @Test
        void createPaymentIntent_vehicleHireNotPending_throwsPaymentRequired() {
            when(vehicleHireService.getHireById(30L, tourist))
                    .thenReturn(hire(1L, VehicleHireStatus.CANCELLED, "450.00"));

            PaymentRequestDto request =
                    new PaymentRequestDto(PayableType.VEHICLE_HIRE, 30L, new BigDecimal("450.00"), null);

            assertThatThrownBy(() -> paymentService.createPaymentIntent(request, tourist))
                    .isInstanceOf(PaymentRequiredException.class);
        }
    }

    // ------------------------------------------------------------------
    // handlePaymentSucceeded
    // ------------------------------------------------------------------

    @Nested
    class HandlePaymentSucceeded {

        @Test
        void handlePaymentSucceeded_confirmsBookingAndGeneratesInvoiceOnce() {
            Payment payment = pendingPayment(PayableType.BOOKING, 10L, "pi_1");
            when(paymentRepository.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            paymentService.handlePaymentSucceeded("pi_1");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            verify(bookingService).confirmBookingAfterPayment(10L);
            verify(invoiceRepository).save(any(Invoice.class));
        }

        @Test
        void handlePaymentSucceeded_calledTwiceForSameIntent_isIdempotent() {
            Payment payment = pendingPayment(PayableType.BOOKING, 10L, "pi_1");
            when(paymentRepository.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            paymentService.handlePaymentSucceeded("pi_1");
            paymentService.handlePaymentSucceeded("pi_1");

            // second delivery is a no-op: no double-confirm, no second invoice
            verify(bookingService, times(1)).confirmBookingAfterPayment(10L);
            verify(invoiceRepository, times(1)).save(any(Invoice.class));
            verify(paymentRepository, times(1)).save(any(Payment.class));
        }

        @Test
        void handlePaymentSucceeded_roomReservation_confirmsReservationAfterPayment() {
            Payment payment = pendingPayment(PayableType.ROOM_RESERVATION, 20L, "pi_2");
            when(paymentRepository.findByStripePaymentIntentId("pi_2")).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            paymentService.handlePaymentSucceeded("pi_2");

            verify(roomReservationService).confirmReservationAfterPayment(20L);
            verify(invoiceRepository).save(any(Invoice.class));
        }

        @Test
        void handlePaymentSucceeded_vehicleHire_confirmsHireAfterPayment() {
            Payment payment = pendingPayment(PayableType.VEHICLE_HIRE, 30L, "pi_3");
            when(paymentRepository.findByStripePaymentIntentId("pi_3")).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            paymentService.handlePaymentSucceeded("pi_3");

            verify(vehicleHireService).confirmHireAfterPayment(30L);
            verify(invoiceRepository).save(any(Invoice.class));
        }

        @Test
        void handlePaymentSucceeded_confirmationFails_stillMarksPaidAndGeneratesInvoice() {
            Payment payment = pendingPayment(PayableType.BOOKING, 10L, "pi_1");
            when(paymentRepository.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
            doThrow(new RuntimeException("booking was cancelled")).when(bookingService).confirmBookingAfterPayment(10L);

            paymentService.handlePaymentSucceeded("pi_1"); // swallowed — webhook must still be ACKed

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            verify(invoiceRepository).save(any(Invoice.class));
        }

        @Test
        void handlePaymentSucceeded_unknownIntent_isIgnoredSoStripeStopsRetrying() {
            when(paymentRepository.findByStripePaymentIntentId("pi_missing")).thenReturn(Optional.empty());

            paymentService.handlePaymentSucceeded("pi_missing"); // must not throw

            verify(bookingService, never()).confirmBookingAfterPayment(any());
            verify(invoiceRepository, never()).save(any());
        }

    }

    // ------------------------------------------------------------------
    // handlePaymentFailed
    // ------------------------------------------------------------------

    @Nested
    class HandlePaymentFailed {

        @Test
        void handlePaymentFailed_pendingPayment_movesToFailed() {
            Payment payment = pendingPayment(PayableType.BOOKING, 10L, "pi_1");
            when(paymentRepository.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(payment));

            paymentService.handlePaymentFailed("pi_1");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(paymentRepository).save(payment);
        }

        @Test
        void handlePaymentFailed_alreadySucceeded_doesNotChangeStatus() {
            Payment payment = pendingPayment(PayableType.BOOKING, 10L, "pi_1");
            payment.setStatus(PaymentStatus.SUCCEEDED);
            when(paymentRepository.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(payment));

            paymentService.handlePaymentFailed("pi_1");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        void handlePaymentFailed_unknownIntent_isIgnoredSoStripeStopsRetrying() {
            when(paymentRepository.findByStripePaymentIntentId("pi_missing")).thenReturn(Optional.empty());

            paymentService.handlePaymentFailed("pi_missing"); // must not throw
            verify(paymentRepository, never()).save(any());
        }
    }

    // ------------------------------------------------------------------
    // refundPayment
    // ------------------------------------------------------------------

    @Nested
    class RefundPayment {

        @Test
        void refundPayment_succeededPayment_delegatesToRefundGatewayForFullAmount() {
            Payment payment = pendingPayment(PayableType.BOOKING, 10L, "pi_1");
            payment.setStatus(PaymentStatus.SUCCEEDED);
            when(paymentRepository.findById(55L)).thenReturn(Optional.of(payment));
            Payment refunded = pendingPayment(PayableType.BOOKING, 10L, "pi_1");
            refunded.setStatus(PaymentStatus.REFUNDED);
            when(refundGateway.refund(payment, payment.getAmount())).thenReturn(refunded);

            PaymentResponseDto result = paymentService.refundPayment(55L);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            verify(refundGateway).refund(payment, new BigDecimal("200.00"));
        }

        @Test
        void refundPayment_pendingPayment_throwsInvalidStatusTransitionAndNeverCallsGateway() {
            Payment payment = pendingPayment(PayableType.BOOKING, 10L, "pi_1");
            when(paymentRepository.findById(55L)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.refundPayment(55L))
                    .isInstanceOf(InvalidStatusTransitionException.class);
            verify(refundGateway, never()).refund(any(), any());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        void refundPayment_alreadyRefunded_throwsInvalidStatusTransition() {
            Payment payment = pendingPayment(PayableType.BOOKING, 10L, "pi_1");
            payment.setStatus(PaymentStatus.REFUNDED);
            when(paymentRepository.findById(55L)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.refundPayment(55L))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void refundPayment_unknownId_throwsResourceNotFound() {
            when(paymentRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.refundPayment(404L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private BookingResponseDto booking(Long touristId, BookingStatus status, String packagePrice, int travelers) {
        return BookingResponseDto.builder()
                .id(10L)
                .touristId(touristId)
                .status(status)
                .numberOfTravelers(travelers)
                .totalPrice(new BigDecimal(packagePrice).multiply(BigDecimal.valueOf(travelers)))
                .tourPackage(BookingPackageSummaryDto.builder()
                        .id(5L)
                        .title("Hill Country Explorer")
                        .price(new BigDecimal(packagePrice))
                        .build())
                .build();
    }

    private RoomReservationResponseDto reservation(Long touristId, RoomReservationStatus status,
                                                   String pricePerNight, int rooms,
                                                   LocalDate checkIn, LocalDate checkOut) {
        return RoomReservationResponseDto.builder()
                .id(20L)
                .touristId(touristId)
                .status(status)
                .numberOfRooms(rooms)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .room(RoomSummaryDto.builder()
                        .id(3L)
                        .roomType("Deluxe")
                        .pricePerNight(new BigDecimal(pricePerNight))
                        .build())
                .build();
    }

    private VehicleHireResponseDto hire(Long touristId, VehicleHireStatus status, String totalPrice) {
        return VehicleHireResponseDto.builder()
                .id(30L)
                .touristId(touristId)
                .status(status)
                .totalPrice(new BigDecimal(totalPrice))
                .build();
    }

    private Payment pendingPayment(PayableType payableType, Long payableId, String stripeIntentId) {
        return Payment.builder()
                .id(55L)
                .payer(tourist)
                .payableType(payableType)
                .payableId(payableId)
                .amount(new BigDecimal("200.00"))
                .currency("usd")
                .stripePaymentIntentId(stripeIntentId)
                .status(PaymentStatus.PENDING)
                .build();
    }

    private Payment withId(Payment payment, long id) {
        payment.setId(id);
        return payment;
    }

    private PaymentIntent stripeIntent(String id, String clientSecret) {
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getId()).thenReturn(id);
        when(intent.getClientSecret()).thenReturn(clientSecret);
        return intent;
    }

    // ------------------------------------------------------------------
    // deletePayment / deletePayments (admin clean-up)
    // ------------------------------------------------------------------

    @Nested
    class DeletePayment {

        private User admin = User.builder().id(9L).role(Role.ADMIN).build();

        private Payment payment(Long id, PaymentStatus status, String intentId) {
            return Payment.builder().id(id).payer(tourist).payableType(PayableType.BOOKING).payableId(5L)
                    .amount(new BigDecimal("100.00")).currency("usd").stripePaymentIntentId(intentId)
                    .status(status).build();
        }

        @Test
        void failedAndPendingWithoutIntent_areDeleted() {
            Payment failed = payment(1L, PaymentStatus.FAILED, "pi_f");
            Payment pending = payment(2L, PaymentStatus.PENDING, null);
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(failed));
            when(paymentRepository.findById(2L)).thenReturn(Optional.of(pending));
            when(invoiceRepository.findByPaymentId(any())).thenReturn(Optional.empty());

            paymentService.deletePayment(1L, admin);
            paymentService.deletePayment(2L, admin);

            verify(paymentRepository).delete(failed);
            verify(paymentRepository).delete(pending);
            verify(bookingService, never()).cancelBooking(any(), any());
        }

        @Test
        void refunded_isDeletedTogetherWithItsInvoice() {
            Payment refunded = payment(3L, PaymentStatus.REFUNDED, "pi_r");
            Invoice invoice = Invoice.builder().id(30L).payment(refunded).build();
            when(paymentRepository.findById(3L)).thenReturn(Optional.of(refunded));
            when(invoiceRepository.findByPaymentId(3L)).thenReturn(Optional.of(invoice));

            paymentService.deletePayment(3L, admin);

            org.mockito.InOrder order = org.mockito.Mockito.inOrder(invoiceRepository, paymentRepository);
            order.verify(invoiceRepository).delete(invoice);
            order.verify(paymentRepository).delete(refunded);
        }

        @Test
        void succeededAndRefundPending_areRejectedWithTheClearMessage() {
            for (PaymentStatus status : new PaymentStatus[] {PaymentStatus.SUCCEEDED, PaymentStatus.REFUND_PENDING}) {
                Payment protectedPayment = payment(4L, status, "pi_s");
                when(paymentRepository.findById(4L)).thenReturn(Optional.of(protectedPayment));

                assertThatThrownBy(() -> paymentService.deletePayment(4L, admin))
                        .isInstanceOf(InvalidStatusTransitionException.class)
                        .hasMessage("Payments that have succeeded or have a refund in progress cannot be deleted.");
                verify(paymentRepository, never()).delete(protectedPayment);
            }
        }

        @Test
        void pending_alreadySucceededInStripe_abortsTheDelete() throws Exception {
            Payment pending = payment(5L, PaymentStatus.PENDING, "pi_paid");
            when(paymentRepository.findById(5L)).thenReturn(Optional.of(pending));
            com.stripe.model.PaymentIntent intent = org.mockito.Mockito.mock(com.stripe.model.PaymentIntent.class);
            when(intent.getStatus()).thenReturn("succeeded");

            try (org.mockito.MockedStatic<com.stripe.model.PaymentIntent> stripe =
                         org.mockito.Mockito.mockStatic(com.stripe.model.PaymentIntent.class)) {
                stripe.when(() -> com.stripe.model.PaymentIntent.retrieve("pi_paid")).thenReturn(intent);

                assertThatThrownBy(() -> paymentService.deletePayment(5L, admin))
                        .isInstanceOf(InvalidStatusTransitionException.class)
                        .hasMessageContaining("refresh");
            }
            verify(paymentRepository, never()).delete(pending);
            verify(intent, never()).cancel();
        }

        @Test
        void pending_openInStripe_isCancelledThenDeleted() throws Exception {
            Payment pending = payment(6L, PaymentStatus.PENDING, "pi_open");
            when(paymentRepository.findById(6L)).thenReturn(Optional.of(pending));
            when(invoiceRepository.findByPaymentId(6L)).thenReturn(Optional.empty());
            com.stripe.model.PaymentIntent intent = org.mockito.Mockito.mock(com.stripe.model.PaymentIntent.class);
            when(intent.getStatus()).thenReturn("requires_payment_method");

            try (org.mockito.MockedStatic<com.stripe.model.PaymentIntent> stripe =
                         org.mockito.Mockito.mockStatic(com.stripe.model.PaymentIntent.class)) {
                stripe.when(() -> com.stripe.model.PaymentIntent.retrieve("pi_open")).thenReturn(intent);

                paymentService.deletePayment(6L, admin);
            }
            verify(intent).cancel();
            verify(paymentRepository).delete(pending);
        }

        @Test
        void bulk_skipsProtectedAndMissingRows_andDeletesTheRest() {
            Payment ok = payment(1L, PaymentStatus.FAILED, "pi_f");
            Payment succeeded = payment(2L, PaymentStatus.SUCCEEDED, "pi_s");
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(ok));
            when(paymentRepository.findById(2L)).thenReturn(Optional.of(succeeded));
            when(paymentRepository.findById(3L)).thenReturn(Optional.empty());
            when(invoiceRepository.findByPaymentId(1L)).thenReturn(Optional.empty());

            var result = paymentService.deletePayments(java.util.List.of(1L, 2L, 3L, 1L), admin);

            assertThat(result.getDeleted()).isEqualTo(1);
            assertThat(result.getSkipped()).extracting(s -> s.getId()).containsExactly(2L, 3L);
            assertThat(result.getSkipped().get(0).getReason()).contains("cannot be deleted");
            verify(paymentRepository).delete(ok);
            verify(paymentRepository, never()).delete(succeeded);
        }

        @Test
        void bulk_moreThan200_isRejected() {
            java.util.List<Long> ids = java.util.stream.LongStream.rangeClosed(1, 201).boxed().toList();

            assertThatThrownBy(() -> paymentService.deletePayments(ids, admin))
                    .isInstanceOf(com.tourlk.exception.BadRequestException.class);
        }
    }

}
