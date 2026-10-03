package com.tourlk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tourlk.entity.RoomReservation;
import com.tourlk.entity.VehicleHire;
import com.tourlk.enums.PayableType;
import com.tourlk.enums.RoomReservationStatus;
import com.tourlk.enums.VehicleHireStatus;
import com.tourlk.repo.PaymentRepository;
import com.tourlk.repo.RoomReservationRepository;
import com.tourlk.repo.VehicleHireRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnpaidHoldExpiryJobTest {

    @Mock private VehicleHireRepository vehicleHireRepository;
    @Mock private RoomReservationRepository roomReservationRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private VehicleHireService vehicleHireService;
    @Mock private RoomReservationService roomReservationService;
    @Mock private BookingService bookingService;

    @InjectMocks private UnpaidHoldExpiryJob job;

    private final LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);

    @Test
    void expiresUnpaidHireAndReservation() {
        VehicleHire hire = VehicleHire.builder().id(5L).build();
        RoomReservation reservation = RoomReservation.builder().id(6L).build();
        when(vehicleHireRepository.findByStatusAndCreatedAtBefore(VehicleHireStatus.PENDING, cutoff))
                .thenReturn(List.of(hire));
        when(roomReservationRepository.findByStatusAndCreatedAtBefore(RoomReservationStatus.PENDING, cutoff))
                .thenReturn(List.of(reservation));
        when(paymentRepository.existsByPayableTypeAndPayableIdAndStatusIn(any(), any(), any())).thenReturn(false);
        when(vehicleHireService.expireUnpaidHire(5L)).thenReturn(true);
        when(roomReservationService.expireUnpaidReservation(6L)).thenReturn(true);

        assertThat(job.expireUnpaidHolds(cutoff)).isEqualTo(2);
    }

    @Test
    void skipsHoldsWithAPaymentInProgress() {
        VehicleHire hire = VehicleHire.builder().id(5L).build();
        when(vehicleHireRepository.findByStatusAndCreatedAtBefore(VehicleHireStatus.PENDING, cutoff))
                .thenReturn(List.of(hire));
        when(roomReservationRepository.findByStatusAndCreatedAtBefore(RoomReservationStatus.PENDING, cutoff))
                .thenReturn(List.of());
        when(paymentRepository.existsByPayableTypeAndPayableIdAndStatusIn(
                org.mockito.ArgumentMatchers.eq(PayableType.VEHICLE_HIRE), org.mockito.ArgumentMatchers.eq(5L), any()))
                .thenReturn(true);

        assertThat(job.expireUnpaidHolds(cutoff)).isZero();
        verify(vehicleHireService, never()).expireUnpaidHire(any());
    }

    @Test
    void addOnsExpireWithTheirUnpaidBooking_notOnTheirOwn() {
        com.tourlk.entity.Booking booking = com.tourlk.entity.Booking.builder().id(9L).build();
        VehicleHire linked = VehicleHire.builder().id(5L).booking(booking).build();
        when(vehicleHireRepository.findByStatusAndCreatedAtBefore(VehicleHireStatus.PENDING, cutoff))
                .thenReturn(List.of(linked));
        when(roomReservationRepository.findByStatusAndCreatedAtBefore(RoomReservationStatus.PENDING, cutoff))
                .thenReturn(List.of());
        when(paymentRepository.existsByPayableTypeAndPayableIdAndStatusIn(
                org.mockito.ArgumentMatchers.eq(PayableType.BOOKING), org.mockito.ArgumentMatchers.eq(9L), any()))
                .thenReturn(false);
        when(bookingService.expireUnpaidBooking(9L)).thenReturn(true);

        assertThat(job.expireUnpaidHolds(cutoff)).isEqualTo(1);
        verify(vehicleHireService, never()).expireUnpaidHire(any());
    }

    @Test
    void addOnsOfABookingBeingPaid_areLeftAlone() {
        com.tourlk.entity.Booking booking = com.tourlk.entity.Booking.builder().id(9L).build();
        VehicleHire linked = VehicleHire.builder().id(5L).booking(booking).build();
        when(vehicleHireRepository.findByStatusAndCreatedAtBefore(VehicleHireStatus.PENDING, cutoff))
                .thenReturn(List.of(linked));
        when(roomReservationRepository.findByStatusAndCreatedAtBefore(RoomReservationStatus.PENDING, cutoff))
                .thenReturn(List.of());
        when(paymentRepository.existsByPayableTypeAndPayableIdAndStatusIn(
                org.mockito.ArgumentMatchers.eq(PayableType.BOOKING), org.mockito.ArgumentMatchers.eq(9L), any()))
                .thenReturn(true);

        assertThat(job.expireUnpaidHolds(cutoff)).isZero();
        verify(bookingService, never()).expireUnpaidBooking(any());
    }

}
