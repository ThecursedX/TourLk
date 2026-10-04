package com.tourlk.service;

import com.tourlk.dto.ClosureSummaryDto;
import com.tourlk.entity.Booking;
import com.tourlk.entity.Payment;
import com.tourlk.enums.BookingStatus;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.PayableType;
import com.tourlk.enums.PaymentStatus;
import com.tourlk.repo.BookingRepository;
import com.tourlk.repo.PaymentRepository;
import com.tourlk.util.ClosureWindow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the active bookings a destination closure overlaps and cancels them with a
 * 100% refund. This is the platform cancelling, so the tourist-facing
 * {@link CancellationPolicy} percentage does not apply.
 * <p>
 * Each booking is handled in its own transaction (REQUIRES_NEW), so a failed refund
 * rolls back only that booking (it stays active and untouched) and never blocks the rest;
 * failures are logged and reported in the summary.
 */
@Slf4j
@Component
public class DestinationClosureBookingService {

    /** SQL Server's DATE tops out at 9999-12-31, so LocalDate.MAX can't be used as an open end. */
    private static final LocalDate FAR_FUTURE = LocalDate.of(9999, 12, 31);

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final RefundGateway refundGateway;
    private final NotificationService notificationService;
    private final RoomReservationService roomReservationService;
    private final VehicleHireService vehicleHireService;
    private final TransactionTemplate perBookingTransaction;

    public DestinationClosureBookingService(BookingRepository bookingRepository,
                                            PaymentRepository paymentRepository,
                                            RefundGateway refundGateway,
                                            NotificationService notificationService,
                                            // @Lazy: AccommodationService -> DestinationService -> this -> room reservations is a cycle.
                                            @Lazy RoomReservationService roomReservationService,
                                            @Lazy VehicleHireService vehicleHireService,
                                            PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.refundGateway = refundGateway;
        this.notificationService = notificationService;
        this.roomReservationService = roomReservationService;
        this.vehicleHireService = vehicleHireService;
        this.perBookingTransaction = new TransactionTemplate(transactionManager);
        this.perBookingTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Active bookings at the destination whose trip dates overlap {@code window}. Read-only. */
    public List<Booking> findAffected(Long destinationId, ClosureWindow window) {
        LocalDate latestStart = window.until() == null ? FAR_FUTURE : window.until();
        return bookingRepository
                .findActiveByDestinationStartingOnOrBefore(destinationId, latestStart, BookingStatus.TERMINAL)
                .stream()
                .filter(b -> window.overlaps(b.getTravelDate(), b.getTourPackage().getDurationDays()))
                .toList();
    }

    public int countAffected(Long destinationId, ClosureWindow window) {
        return findAffected(destinationId, window).size();
    }

    /** Cancels and fully refunds every affected booking, one transaction each. */
    public ClosureSummaryDto cancelAffected(Long destinationId, ClosureWindow window, String closureReason) {
        int cancelled = 0;
        int refunded = 0;
        List<Long> failed = new ArrayList<>();

        for (Booking affected : findAffected(destinationId, window)) {
            Long bookingId = affected.getId();
            try {
                Boolean wasRefunded = perBookingTransaction.execute(status -> cancelOne(bookingId, closureReason));
                if (wasRefunded != null) {
                    cancelled++;
                    if (wasRefunded) {
                        refunded++;
                    }
                }
            } catch (RuntimeException e) {
                log.error("Could not cancel/refund booking {} for the closure of destination {}",
                        bookingId, destinationId, e);
                failed.add(bookingId);
            }
        }

        return ClosureSummaryDto.builder()
                .cancelledBookings(cancelled)
                .refundedCount(refunded)
                .failedRefunds(failed.size())
                .failedBookingIds(failed)
                .build();
    }

    /**
     * @return whether a payment was refunded, or {@code null} if the booking was skipped
     *         because it reached a terminal status in the meantime
     */
    private Boolean cancelOne(Long bookingId, String closureReason) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null || BookingStatus.TERMINAL.contains(booking.getStatus())) {
            return null;
        }

        boolean refunded = false;
        for (Payment payment : paymentRepository.findByPayableTypeAndPayableId(PayableType.BOOKING, bookingId)) {
            if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                refundGateway.refund(payment, payment.getAmount());
                refunded = true;
            } else if (payment.getStatus() == PaymentStatus.PENDING) {
                refundGateway.cancelUncompletedPayment(payment);
            }
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setRejectionReason("Destination temporarily closed: " + closureReason);
        booking.setRequestedTravelDate(null);
        booking.setStatusBeforeReschedule(null);
        bookingRepository.save(booking);
        roomReservationService.cancelLinkedToBooking(bookingId);
        vehicleHireService.cancelLinkedToBooking(bookingId);

        String title = booking.getTourPackage().getTitle();
        notificationService.notify(booking.getTourist(), NotificationType.BOOKING_CANCELLED, "Booking cancelled",
                "Your booking for " + title + " was cancelled because the destination is temporarily closed ("
                        + closureReason + ")." + (refunded ? " A full refund has been issued." : ""),
                "/bookings/mine");
        notificationService.notify(booking.getTourPackage().getCreatedBy(), NotificationType.BOOKING_CANCELLED,
                "Booking cancelled",
                booking.getTourist().getName() + "'s booking for " + title + " on " + booking.getTravelDate()
                        + " was cancelled because the destination is temporarily closed.",
                "/bookings/" + bookingId);

        return refunded;
    }

}
