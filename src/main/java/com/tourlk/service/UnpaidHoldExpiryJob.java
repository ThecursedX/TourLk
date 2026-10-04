package com.tourlk.service;

import com.tourlk.entity.RoomReservation;
import com.tourlk.entity.VehicleHire;
import com.tourlk.enums.PayableType;
import com.tourlk.enums.PaymentStatus;
import com.tourlk.enums.RoomReservationStatus;
import com.tourlk.enums.VehicleHireStatus;
import com.tourlk.repo.PaymentRepository;
import com.tourlk.repo.RoomReservationRepository;
import com.tourlk.repo.VehicleHireRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * PENDING vehicle hires and room reservations hold their dates, so an abandoned
 * (never paid) one would block them forever. This cancels PENDING ones older than
 * {@code app.holds.expiry-minutes} that have no PENDING or SUCCEEDED payment (add-ons of a package booking
 * expire together with that booking, judged by the booking's payments): a
 * PENDING payment means the tourist is mid-checkout, a SUCCEEDED one is just
 * waiting for its webhook to confirm the booking.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnpaidHoldExpiryJob {

    private static final Set<PaymentStatus> PAYMENT_IN_PROGRESS =
            EnumSet.of(PaymentStatus.PENDING, PaymentStatus.SUCCEEDED);

    private final VehicleHireRepository vehicleHireRepository;
    private final RoomReservationRepository roomReservationRepository;
    private final PaymentRepository paymentRepository;
    private final VehicleHireService vehicleHireService;
    private final RoomReservationService roomReservationService;
    private final BookingService bookingService;

    @Value("${app.holds.expiry-minutes:30}")
    private long expiryMinutes;

    @Scheduled(cron = "${app.holds.cleanup-cron:0 */5 * * * *}")
    public void expireUnpaidHolds() {
        int expired = expireUnpaidHolds(LocalDateTime.now().minusMinutes(expiryMinutes));
        if (expired > 0) {
            log.info("Expired {} unpaid vehicle hire(s)/room reservation(s)", expired);
        }
    }

    /** @return how many holds were cancelled. */
    int expireUnpaidHolds(LocalDateTime cutoff) {
        int expired = 0;

        for (VehicleHire hire : vehicleHireRepository.findByStatusAndCreatedAtBefore(
                VehicleHireStatus.PENDING, cutoff)) {
            if (hire.getBooking() != null) {
                expired += expireLinked(hire.getBooking().getId());
                continue;
            }
            if (!paymentRepository.existsByPayableTypeAndPayableIdAndStatusIn(
                    PayableType.VEHICLE_HIRE, hire.getId(), PAYMENT_IN_PROGRESS)
                    && vehicleHireService.expireUnpaidHire(hire.getId())) {
                expired++;
            }
        }

        for (RoomReservation reservation : roomReservationRepository.findByStatusAndCreatedAtBefore(
                RoomReservationStatus.PENDING, cutoff)) {
            if (reservation.getBooking() != null) {
                expired += expireLinked(reservation.getBooking().getId());
                continue;
            }
            if (!paymentRepository.existsByPayableTypeAndPayableIdAndStatusIn(
                    PayableType.ROOM_RESERVATION, reservation.getId(), PAYMENT_IN_PROGRESS)
                    && roomReservationService.expireUnpaidReservation(reservation.getId())) {
                expired++;
            }
        }

        return expired;
    }

    /**
     * Add-ons are paid for by their package booking, so they expire with it: an unpaid PENDING booking
     * is cancelled (which releases its rooms/vehicles) unless a payment for it is in progress.
     */
    private int expireLinked(Long bookingId) {
        if (paymentRepository.existsByPayableTypeAndPayableIdAndStatusIn(
                PayableType.BOOKING, bookingId, PAYMENT_IN_PROGRESS)) {
            return 0;
        }
        return bookingService.expireUnpaidBooking(bookingId) ? 1 : 0;
    }

}
