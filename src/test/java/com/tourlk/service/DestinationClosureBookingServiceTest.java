package com.tourlk.service;

import com.tourlk.dto.ClosureSummaryDto;
import com.tourlk.entity.Booking;
import com.tourlk.entity.Destination;
import com.tourlk.entity.Payment;
import com.tourlk.entity.TourPackage;
import com.tourlk.entity.User;
import com.tourlk.enums.BookingStatus;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.PayableType;
import com.tourlk.enums.PaymentStatus;
import com.tourlk.enums.Role;
import com.tourlk.exception.PaymentGatewayException;
import com.tourlk.repo.BookingRepository;
import com.tourlk.repo.PaymentRepository;
import com.tourlk.util.ClosureWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DestinationClosureBookingService}: which bookings a closure window
 * catches, the 100% refund (not the cancellation policy), pending-payment cancellation,
 * notifications, and one failing booking not blocking the others.
 */
@ExtendWith(MockitoExtension.class)
class DestinationClosureBookingServiceTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RefundGateway refundGateway;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private DestinationClosureBookingService service;

    private User tourist;
    private User guide;
    private TourPackage tourPackage;

    @BeforeEach
    void setUp() {
        service = new DestinationClosureBookingService(
                bookingRepository, paymentRepository, refundGateway, notificationService, transactionManager);
        tourist = User.builder().id(1L).name("Tess").role(Role.TOURIST).build();
        guide = User.builder().id(3L).name("Gina").role(Role.GUIDE).build();
        tourPackage = TourPackage.builder().id(100L).title("Hill Country").durationDays(3)
                .destination(Destination.builder().id(7L).name("Kandy").build())
                .createdBy(guide).build();
    }

    private Booking booking(long id, BookingStatus status, int startsInDays) {
        return Booking.builder().id(id).tourist(tourist).tourPackage(tourPackage)
                .travelDate(TODAY.plusDays(startsInDays)).numberOfTravelers(2).status(status).build();
    }

    private Payment payment(PaymentStatus status) {
        return Payment.builder().id(55L).payer(tourist).payableType(PayableType.BOOKING).payableId(5L)
                .amount(new BigDecimal("240.00")).currency("usd").stripePaymentIntentId("pi_1").status(status).build();
    }

    private void stubCandidates(Booking... bookings) {
        when(bookingRepository.findActiveByDestinationStartingOnOrBefore(eq(7L), any(), any()))
                .thenReturn(List.of(bookings));
        for (Booking b : bookings) {
            lenient(b);
        }
    }

    private void lenient(Booking b) {
        org.mockito.Mockito.lenient().when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
    }

    @Test
    void findAffected_onlyKeepsTripsThatOverlapTheWindow() {
        Booking inside = booking(1, BookingStatus.CONFIRMED, 12);
        Booking overlapsStart = booking(2, BookingStatus.PENDING, 8);      // D+8..D+10
        Booking before = booking(3, BookingStatus.CONFIRMED, 5);           // D+5..D+7
        Booking after = booking(4, BookingStatus.CONFIRMED, 25);
        stubCandidates(inside, overlapsStart, before, after);

        List<Booking> affected = service.findAffected(7L, new ClosureWindow(TODAY.plusDays(10), TODAY.plusDays(20)));

        assertThat(affected).extracting(Booking::getId).containsExactly(1L, 2L);
    }

    @Test
    void cancelAffected_refundsSucceededPaymentInFull_andNotifiesTouristAndGuide() {
        Booking booking = booking(5, BookingStatus.CONFIRMED, 12);
        Payment paid = payment(PaymentStatus.SUCCEEDED);
        stubCandidates(booking);
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.BOOKING, 5L)).thenReturn(List.of(paid));

        ClosureSummaryDto summary = service.cancelAffected(7L,
                new ClosureWindow(TODAY.plusDays(10), TODAY.plusDays(20)), "Bridge repairs");

        assertThat(summary.getCancelledBookings()).isEqualTo(1);
        assertThat(summary.getRefundedCount()).isEqualTo(1);
        assertThat(summary.getFailedRefunds()).isZero();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getRejectionReason()).isEqualTo("Destination temporarily closed: Bridge repairs");
        // 100% of the payment, regardless of how close the trip is (no CancellationPolicy involved).
        verify(refundGateway).refund(paid, new BigDecimal("240.00"));
        verify(bookingRepository).save(booking);
        verify(notificationService).notify(eq(tourist), eq(NotificationType.BOOKING_CANCELLED), any(),
                org.mockito.ArgumentMatchers.contains("A full refund has been issued"), eq("/bookings/mine"));
        verify(notificationService).notify(eq(guide), eq(NotificationType.BOOKING_CANCELLED), any(), any(), any());
    }

    @Test
    void cancelAffected_pendingPayment_isCancelledNotRefunded() {
        Booking booking = booking(5, BookingStatus.PENDING, 12);
        Payment pending = payment(PaymentStatus.PENDING);
        stubCandidates(booking);
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.BOOKING, 5L)).thenReturn(List.of(pending));

        ClosureSummaryDto summary = service.cancelAffected(7L,
                new ClosureWindow(TODAY.plusDays(10), null), "Repairs");

        assertThat(summary.getCancelledBookings()).isEqualTo(1);
        assertThat(summary.getRefundedCount()).isZero();
        verify(refundGateway).cancelUncompletedPayment(pending);
        verify(refundGateway, never()).refund(any(), any());
        verify(notificationService).notify(eq(tourist), eq(NotificationType.BOOKING_CANCELLED), any(),
                org.mockito.ArgumentMatchers.argThat(m -> !m.contains("refund")), eq("/bookings/mine"));
    }

    @Test
    void cancelAffected_oneFailedRefund_doesNotBlockTheOthers() {
        Booking failing = booking(5, BookingStatus.CONFIRMED, 12);
        Booking fine = booking(6, BookingStatus.CONFIRMED, 14);
        Payment paid = payment(PaymentStatus.SUCCEEDED);
        stubCandidates(failing, fine);
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.BOOKING, 5L)).thenReturn(List.of(paid));
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.BOOKING, 6L)).thenReturn(List.of());
        when(refundGateway.refund(any(), any())).thenThrow(new PaymentGatewayException("Stripe is down", null));

        ClosureSummaryDto summary = service.cancelAffected(7L,
                new ClosureWindow(TODAY.plusDays(10), TODAY.plusDays(20)), "Repairs");

        assertThat(summary.getCancelledBookings()).isEqualTo(1);
        assertThat(summary.getFailedRefunds()).isEqualTo(1);
        assertThat(summary.getFailedBookingIds()).containsExactly(5L);
        // The failed booking was never saved as cancelled; the other one was.
        verify(bookingRepository, never()).save(failing);
        verify(bookingRepository).save(fine);
        assertThat(fine.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancelAffected_bookingTurnedTerminalMeanwhile_isSkipped() {
        Booking stale = booking(5, BookingStatus.CONFIRMED, 12);
        when(bookingRepository.findActiveByDestinationStartingOnOrBefore(eq(7L), any(), any()))
                .thenReturn(List.of(stale));
        Booking current = booking(5, BookingStatus.CANCELLED, 12);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(current));

        ClosureSummaryDto summary = service.cancelAffected(7L,
                new ClosureWindow(TODAY.plusDays(10), TODAY.plusDays(20)), "Repairs");

        assertThat(summary.getCancelledBookings()).isZero();
        verify(refundGateway, never()).refund(any(), any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void countAffected_changesNothing() {
        stubCandidates(booking(1, BookingStatus.CONFIRMED, 12), booking(2, BookingStatus.PENDING, 50));

        assertThat(service.countAffected(7L, new ClosureWindow(TODAY.plusDays(10), TODAY.plusDays(20)))).isEqualTo(1);
        verify(bookingRepository, never()).save(any());
        verify(refundGateway, never()).refund(any(), any());
    }

}
