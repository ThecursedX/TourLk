package com.tourlk.service;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import com.tourlk.enums.NotificationType;
import com.tourlk.dto.RoomReservationRequestDto;
import com.tourlk.dto.RoomReservationResponseDto;
import com.tourlk.entity.Accommodation;
import com.tourlk.entity.TourPackage;
import com.tourlk.entity.Booking;
import com.tourlk.entity.Destination;
import com.tourlk.dto.CancellationPreviewResponseDto;
import com.tourlk.entity.Payment;
import com.tourlk.entity.Room;
import com.tourlk.entity.RoomReservation;
import com.tourlk.entity.User;
import com.tourlk.enums.AccommodationStatus;
import com.tourlk.enums.BookingStatus;
import com.tourlk.enums.DestinationStatus;
import com.tourlk.enums.PayableType;
import com.tourlk.enums.PaymentStatus;
import com.tourlk.enums.Role;
import com.tourlk.enums.RoomReservationStatus;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.InvalidDateRangeException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.exception.RoomUnavailableException;
import com.tourlk.repo.PaymentRepository;
import com.tourlk.repo.RoomRepository;
import com.tourlk.repo.RoomReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RoomReservationServiceImpl}: create/confirm/cancel/
 * complete plus date-range, availability, ownership and status guards.
 */
@ExtendWith(MockitoExtension.class)
class RoomReservationServiceImplTest {

    @Mock
    private RoomReservationRepository roomReservationRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private AccommodationService accommodationService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RefundGateway refundGateway;
    @Mock
    private RoomCancellationPolicy cancellationPolicy;

    @InjectMocks
    private RoomReservationServiceImpl service;

    private User tourist;
    private User stranger;
    private User hotelOwner;
    private User admin;
    private Accommodation accommodation;
    private Room room;

    @BeforeEach
    void setUp() {
        tourist = User.builder().id(1L).name("Tess").role(Role.TOURIST).build();
        stranger = User.builder().id(2L).name("Stan").role(Role.TOURIST).build();
        hotelOwner = User.builder().id(3L).name("Holly Host").role(Role.HOTEL_PARTNER).build();
        admin = User.builder().id(9L).name("Amy Admin").role(Role.ADMIN).build();
        accommodation = Accommodation.builder()
                .id(50L).name("Ocean View")
                .location(Destination.builder()
                        .id(1L).name("Galle").region("Southern Province").status(DestinationStatus.PUBLISHED).build())
                .status(AccommodationStatus.ACTIVE).owner(hotelOwner)
                .build();
        room = Room.builder()
                .id(60L).accommodation(accommodation).roomType("Deluxe")
                .pricePerNight(new BigDecimal("80.00")).totalRooms(5).maxOccupancy(2)
                .build();
    }

    private RoomReservation reservation(RoomReservationStatus status, User owner) {
        return RoomReservation.builder()
                .id(7L).tourist(owner).room(room)
                .checkInDate(LocalDate.now().plusDays(10)).checkOutDate(LocalDate.now().plusDays(13))
                .numberOfRooms(1).status(status)
                .build();
    }

    // ------------------------------------------------------------------
    // createReservation
    // ------------------------------------------------------------------

    @Test
    void createReservation_validAndAvailable_savesPending() {
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));
        when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(0);
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> {
            RoomReservation r = inv.getArgument(0);
            r.setId(7L);
            return r;
        });

        RoomReservationRequestDto request = new RoomReservationRequestDto(
                60L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 1, 1);
        RoomReservationResponseDto result = service.createReservation(request, tourist);

        assertThat(result.getStatus()).isEqualTo(RoomReservationStatus.PENDING);
        assertThat(result.getTouristId()).isEqualTo(1L);
        assertThat(result.getRoom().getAccommodationId()).isEqualTo(50L);
    }

    @Test
    void toResponse_roomImagePreferredOverAccommodationImage() {
        room.setImageUrls(java.util.List.of("https://img/room.jpg"));
        accommodation.setImageUrls(java.util.List.of("https://img/hotel.jpg"));
        when(roomReservationRepository.findById(7L))
                .thenReturn(Optional.of(reservation(RoomReservationStatus.PENDING, tourist)));

        assertThat(service.getReservationById(7L, tourist).getRoom().getCoverImageUrl())
                .isEqualTo("https://img/room.jpg");
    }

    @Test
    void toResponse_roomWithoutImages_fallsBackToAccommodationImage() {
        accommodation.setImageUrls(java.util.List.of("https://img/hotel.jpg"));
        when(roomReservationRepository.findById(7L))
                .thenReturn(Optional.of(reservation(RoomReservationStatus.PENDING, tourist)));

        assertThat(service.getReservationById(7L, tourist).getRoom().getCoverImageUrl())
                .isEqualTo("https://img/hotel.jpg");
    }

    @Test
    void toResponse_noImagesAnywhere_coverImageIsNull() {
        when(roomReservationRepository.findById(7L))
                .thenReturn(Optional.of(reservation(RoomReservationStatus.PENDING, tourist)));

        assertThat(service.getReservationById(7L, tourist).getRoom().getCoverImageUrl()).isNull();
    }

    @Test
    void createReservation_checkOutNotAfterCheckIn_throwsInvalidDateRange() {
        LocalDate sameDay = LocalDate.now().plusDays(10);
        RoomReservationRequestDto request = new RoomReservationRequestDto(60L, sameDay, sameDay, 1, 1);

        assertThatThrownBy(() -> service.createReservation(request, tourist))
                .isInstanceOf(InvalidDateRangeException.class);
        verify(roomReservationRepository, never()).save(any());
    }

    @Test
    void createReservation_accommodationNotActive_throwsBadRequest() {
        accommodation.setStatus(AccommodationStatus.INACTIVE);
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));

        RoomReservationRequestDto request = new RoomReservationRequestDto(
                60L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 1, 1);

        assertThatThrownBy(() -> service.createReservation(request, tourist))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createReservation_temporarilyUnavailable_throwsBadRequestAndDoesNotSave() {
        accommodation.setStatus(AccommodationStatus.TEMPORARILY_UNAVAILABLE);
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));

        RoomReservationRequestDto request = new RoomReservationRequestDto(
                60L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 1, 1);

        assertThatThrownBy(() -> service.createReservation(request, tourist))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("temporarily unavailable");
        verify(roomReservationRepository, never()).save(any());
    }

    @Test
    void createReservation_fullyBookedTonight_stillBookableForLaterDatesWithRoomsFree() {
        accommodation.setStatus(AccommodationStatus.FULLY_BOOKED);
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));
        when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(0);
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomReservationRequestDto request = new RoomReservationRequestDto(
                60L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 1, 1);

        assertThat(service.createReservation(request, tourist).getStatus())
                .isEqualTo(RoomReservationStatus.PENDING);
    }

    @Test
    void createReservation_fillsRemainingInventoryExactly_isAllowed() {
        // 5 rooms total, 3 already confirmed on overlapping dates; asking for the last 2 must succeed.
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));
        when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(3);
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomReservationRequestDto request = new RoomReservationRequestDto(
                60L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 2, 1);

        assertThat(service.createReservation(request, tourist).getNumberOfRooms()).isEqualTo(2);
    }

    @Test
    void createReservation_oneRoomOverRemainingInventory_throwsRoomUnavailable() {
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));
        when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(3);

        RoomReservationRequestDto request = new RoomReservationRequestDto(
                60L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 3, 1);

        assertThatThrownBy(() -> service.createReservation(request, tourist))
                .isInstanceOf(RoomUnavailableException.class)
                .hasMessageContaining("Only 2 room(s)");
    }

    @Test
    void createReservation_checksOverlapOnlyAgainstConfirmedReservationsForTheRequestedDates() {
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));
        when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(0);
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));
        LocalDate in = LocalDate.now().plusDays(10);
        LocalDate out = LocalDate.now().plusDays(13);

        service.createReservation(new RoomReservationRequestDto(60L, in, out, 1, 1), tourist);

        verify(roomReservationRepository).sumReservedRoomsOverlapping(60L,
                java.util.EnumSet.of(RoomReservationStatus.PENDING, RoomReservationStatus.CONFIRMED), in, out, 0L);
    }

    @Test
    void confirmReservation_roomFilledByAnotherConfirmationSinceCreation_throwsRoomUnavailable() {
        // Two tourists created PENDING reservations for the last room; the first was confirmed,
        // so confirming the second must re-check inventory under the room lock and refuse.
        RoomReservation pending = reservation(RoomReservationStatus.PENDING, tourist);
        pending.setNumberOfRooms(2);
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(pending));
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));
        when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(4);

        assertThatThrownBy(() -> service.confirmReservationAfterPayment(7L))
                .isInstanceOf(RoomUnavailableException.class);
        assertThat(pending.getStatus()).isEqualTo(RoomReservationStatus.PENDING);
        verify(roomReservationRepository, never()).save(any());
        verify(accommodationService, never()).refreshAvailabilityStatus(any());
    }

    @Test
    void confirmReservation_refreshesAccommodationAvailability() {
        RoomReservation pending = reservation(RoomReservationStatus.PENDING, tourist);
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(pending));
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));
        when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(0);
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.confirmReservationAfterPayment(7L);

        verify(accommodationService).refreshAvailabilityStatus(50L);
    }

    @Test
    void cancelReservation_refreshesAccommodationAvailability() {
        RoomReservation confirmed = reservation(RoomReservationStatus.CONFIRMED, tourist);
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(confirmed));
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelReservation(7L, tourist);

        verify(accommodationService).refreshAvailabilityStatus(50L);
    }

    @Test
    void completeReservation_refreshesAccommodationAvailability() {
        RoomReservation confirmed = reservation(RoomReservationStatus.CONFIRMED, tourist);
        confirmed.setCheckInDate(LocalDate.now());
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(confirmed));
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.completeReservation(7L, hotelOwner);

        verify(accommodationService).refreshAvailabilityStatus(50L);
    }

    @Test
    void createReservation_notEnoughRoomsLeft_throwsRoomUnavailable() {
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));
        when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(5);

        RoomReservationRequestDto request = new RoomReservationRequestDto(
                60L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 1, 1); // 5 + 1 > 5

        assertThatThrownBy(() -> service.createReservation(request, tourist))
                .isInstanceOf(RoomUnavailableException.class);
        verify(roomReservationRepository, never()).save(any());
    }

    @Test
    void createReservation_roomNotFound_throwsResourceNotFound() {
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.empty());

        RoomReservationRequestDto request = new RoomReservationRequestDto(
                60L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 1, 1);

        assertThatThrownBy(() -> service.createReservation(request, tourist))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // confirmReservationAfterPayment — status + property state
    // ------------------------------------------------------------------

    @Test
    void confirmReservationAfterPayment_pending_confirmsWithoutOwnerCheck() {
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(reservation(RoomReservationStatus.PENDING, tourist)));
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));
        when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(0);
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.confirmReservationAfterPayment(7L).getStatus())
                .isEqualTo(RoomReservationStatus.CONFIRMED);
    }

    @Test
    void confirmReservation_notPending_throwsInvalidStatusTransition() {
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(reservation(RoomReservationStatus.CONFIRMED, tourist)));
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> service.confirmReservationAfterPayment(7L))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    // ------------------------------------------------------------------
    // cancelReservation
    // ------------------------------------------------------------------

    @Test
    void cancelReservation_byTourist_cancels() {
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(reservation(RoomReservationStatus.PENDING, tourist)));
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.cancelReservation(7L, tourist).getStatus())
                .isEqualTo(RoomReservationStatus.CANCELLED);
    }

    @Test
    void cancelReservation_byUnrelatedUser_throwsAccessDenied() {
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(reservation(RoomReservationStatus.PENDING, tourist)));

        assertThatThrownBy(() -> service.cancelReservation(7L, stranger))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancelReservation_alreadyCompleted_throwsInvalidStatusTransition() {
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(reservation(RoomReservationStatus.COMPLETED, tourist)));

        assertThatThrownBy(() -> service.cancelReservation(7L, admin))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    // ------------------------------------------------------------------
    // completeReservation / getReservationById
    // ------------------------------------------------------------------

    @Test
    void completeReservation_confirmedByOwner_completes() {
        RoomReservation stay = reservation(RoomReservationStatus.CONFIRMED, tourist);
        stay.setCheckInDate(LocalDate.now().minusDays(1));
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(stay));
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.completeReservation(7L, hotelOwner).getStatus())
                .isEqualTo(RoomReservationStatus.COMPLETED);
    }

    @Test
    void getReservationById_byTourist_returns() {
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(reservation(RoomReservationStatus.PENDING, tourist)));

        assertThat(service.getReservationById(7L, tourist).getId()).isEqualTo(7L);
    }

    @Test
    void getReservationById_byUnrelatedUser_throwsAccessDenied() {
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(reservation(RoomReservationStatus.PENDING, tourist)));

        assertThatThrownBy(() -> service.getReservationById(7L, stranger))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------------------
    // in-app notifications
    // ------------------------------------------------------------------

    @Test
    void createReservation_notifiesThePropertyOwner() {
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));
        when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(0);
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createReservation(new RoomReservationRequestDto(
                60L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 1, 1), tourist);

        verify(notificationService).notify(eq(hotelOwner), eq(NotificationType.ROOM_RESERVATION_REQUESTED), any(), any(),
                eq("/accommodations/owner/reservations"));
    }

    @Test
    void confirmReservationAfterPayment_notifiesBothSides() {
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(reservation(RoomReservationStatus.PENDING, tourist)));
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));
        when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(0);
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.confirmReservationAfterPayment(7L);

        verify(notificationService).notify(eq(tourist), eq(NotificationType.ROOM_RESERVATION_CONFIRMED), any(), any(), any());
        verify(notificationService).notify(eq(hotelOwner), eq(NotificationType.ROOM_RESERVATION_CONFIRMED), any(), any(), any());
    }

    @Test
    void cancelReservation_byTourist_notifiesTheOwner_andByOwnerNotifiesTheTourist() {
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(reservation(RoomReservationStatus.CONFIRMED, tourist)));
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));
        service.cancelReservation(7L, tourist);
        verify(notificationService).notify(eq(hotelOwner), eq(NotificationType.ROOM_RESERVATION_CANCELLED), any(), any(), any());

        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(reservation(RoomReservationStatus.CONFIRMED, tourist)));
        service.cancelReservation(7L, hotelOwner);
        verify(notificationService).notify(eq(tourist), eq(NotificationType.ROOM_RESERVATION_CANCELLED), any(), any(), any());
    }

    @Test
    void completeReservation_notifiesTheTourist() {
        RoomReservation stay = reservation(RoomReservationStatus.CONFIRMED, tourist);
        stay.setCheckInDate(LocalDate.now().minusDays(1));
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(stay));
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.completeReservation(7L, hotelOwner);

        verify(notificationService).notify(eq(tourist), eq(NotificationType.ROOM_RESERVATION_COMPLETED), any(), any(), any());
    }
    // ------------------------------------------------------------------
    // reservations linked to a package booking
    // ------------------------------------------------------------------

    private RoomReservation linkedReservation(RoomReservationStatus status, BookingStatus bookingStatus) {
        RoomReservation r = reservation(status, tourist);
        r.setBooking(Booking.builder().id(5L).status(bookingStatus)
                .tourPackage(TourPackage.builder().title("Hill Country").build()).build());
        return r;
    }

    @Test
    void cancelReservation_linkedToActiveBooking_byTourist_isRejectedWithPointerToTheBooking() {
        when(roomReservationRepository.findById(7L))
                .thenReturn(Optional.of(linkedReservation(RoomReservationStatus.PENDING, BookingStatus.PENDING)));

        assertThatThrownBy(() -> service.cancelReservation(7L, tourist))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("package booking #5");
        verify(roomReservationRepository, never()).save(any());
    }

    private Payment bookingPayment(PaymentStatus status) {
        return Payment.builder().id(98L).payer(tourist).payableType(PayableType.BOOKING).payableId(5L)
                .amount(new BigDecimal("900.00")).currency("usd").status(status).build();
    }

    @Test
    void cancelReservation_linkedAddOn_byOwner_cancelsNotifiesTouristAndFlagsManualRefundOnce() {
        RoomReservation linked = linkedReservation(RoomReservationStatus.CONFIRMED, BookingStatus.CONFIRMED);
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(linked));
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.BOOKING, 5L))
                .thenReturn(List.of(bookingPayment(PaymentStatus.SUCCEEDED)));

        RoomReservationResponseDto result = service.cancelReservation(7L, hotelOwner);

        assertThat(result.getStatus()).isEqualTo(RoomReservationStatus.CANCELLED);
        // Payment tracks one refund total, so no automatic partial refund may touch the booking payment.
        verify(refundGateway, never()).refund(any(), any());
        verify(notificationService).notify(eq(tourist), eq(NotificationType.ROOM_RESERVATION_CANCELLED), any(),
                argThat(m -> m.contains("hotel cancelled") && m.contains("rest of your package booking")),
                eq("/reservations/mine"));
        verify(notificationService).notify(eq(tourist), eq(NotificationType.PAYMENT_REFUNDED), any(),
                argThat(m -> m.contains("240.00") && m.contains("manually")), any());
        verify(notificationService, org.mockito.Mockito.times(1)).notifyAdmins(
                eq(NotificationType.PAYMENT_REFUNDED), any(), argThat(m -> m.contains("240.00")), any());
    }

    @Test
    void cancelReservation_linkedAddOn_byAdmin_isAllowed() {
        RoomReservation linked = linkedReservation(RoomReservationStatus.CONFIRMED, BookingStatus.CONFIRMED);
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(linked));
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.BOOKING, 5L)).thenReturn(List.of());

        assertThat(service.cancelReservation(7L, admin).getStatus()).isEqualTo(RoomReservationStatus.CANCELLED);
        verify(notificationService, never()).notifyAdmins(any(), any(), any(), any());
    }

    @Test
    void cancelForAccommodation_linkedAddOn_flagsManualRefundInsteadOfRefundingTheBookingPayment() {
        RoomReservation linked = linkedReservation(RoomReservationStatus.CONFIRMED, BookingStatus.CONFIRMED);
        when(roomReservationRepository.findByRoomAccommodationIdAndStatusIn(any(), any()))
                .thenReturn(List.of(linked));
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.BOOKING, 5L))
                .thenReturn(List.of(bookingPayment(PaymentStatus.SUCCEEDED)));

        int cancelled = service.cancelForAccommodation(50L,
                java.util.EnumSet.of(RoomReservationStatus.CONFIRMED), "the property was archived.");

        assertThat(cancelled).isEqualTo(1);
        verify(refundGateway, never()).refund(any(), any());
        verify(notificationService).notifyAdmins(eq(NotificationType.PAYMENT_REFUNDED), any(), any(), any());
    }

    @Test
    void cancelForAccommodation_pastPendingStay_isCancelledAndItsPendingPaymentClosed() {
        RoomReservation stale = reservation(RoomReservationStatus.PENDING, tourist);
        stale.setCheckInDate(LocalDate.now().minusDays(5));
        stale.setCheckOutDate(LocalDate.now().minusDays(2));
        Payment unpaid = payment(PaymentStatus.PENDING, "240.00");
        when(roomReservationRepository.findByRoomAccommodationIdAndStatusIn(any(), any()))
                .thenReturn(List.of(stale));
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.ROOM_RESERVATION, 7L))
                .thenReturn(List.of(unpaid));

        int cancelled = service.cancelForAccommodation(50L,
                java.util.EnumSet.of(RoomReservationStatus.PENDING), "the property was archived.");

        assertThat(cancelled).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(RoomReservationStatus.CANCELLED);
        verify(refundGateway).cancelUncompletedPayment(unpaid);
        verify(refundGateway, never()).refund(any(), any());
    }

    @Test
    void cancelLinkedToBooking_cancelsLiveReservationsAndNotifiesTheHotelOwner() {
        RoomReservation live = linkedReservation(RoomReservationStatus.CONFIRMED, BookingStatus.CANCELLED);
        RoomReservation done = linkedReservation(RoomReservationStatus.COMPLETED, BookingStatus.CANCELLED);
        when(roomReservationRepository.findByBookingId(5L)).thenReturn(List.of(live, done));

        service.cancelLinkedToBooking(5L);

        assertThat(live.getStatus()).isEqualTo(RoomReservationStatus.CANCELLED);
        assertThat(done.getStatus()).isEqualTo(RoomReservationStatus.COMPLETED);
        verify(notificationService).notify(eq(hotelOwner), eq(NotificationType.ROOM_RESERVATION_CANCELLED), any(),
                any(), any());
    }

    @Test
    void confirmLinkedAfterPayment_confirmsPendingReservations() {
        RoomReservation pending = linkedReservation(RoomReservationStatus.PENDING, BookingStatus.CONFIRMED);
        when(roomReservationRepository.findByBookingId(5L)).thenReturn(List.of(pending));
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));
        when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(0);
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.confirmLinkedAfterPayment(5L);

        assertThat(pending.getStatus()).isEqualTo(RoomReservationStatus.CONFIRMED);
    }

    // ------------------------------------------------------------------
    // price freeze, occupancy, property state, refunds, completion date
    // ------------------------------------------------------------------

    private void stubCreateFlow() {
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));
        when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(0);
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Payment payment(PaymentStatus status, String amount) {
        return Payment.builder().id(99L).payer(tourist).payableType(PayableType.ROOM_RESERVATION).payableId(7L)
                .amount(new BigDecimal(amount)).currency("usd").status(status).build();
    }

    @Test
    void createReservation_storesFrozenTotalPriceAndGuests() {
        stubCreateFlow();

        RoomReservationResponseDto result = service.createReservation(new RoomReservationRequestDto(
                60L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 2, 3), tourist);

        // 80.00 x 3 nights x 2 rooms
        assertThat(result.getTotalPrice()).isEqualByComparingTo("480.00");
        assertThat(result.getNumberOfGuests()).isEqualTo(3);
    }

    @Test
    void toResponse_legacyReservationWithoutTotal_fallsBackToLivePrice() {
        RoomReservation legacy = reservation(RoomReservationStatus.PENDING, tourist);
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(legacy));

        // 80.00 x 3 nights x 1 room
        assertThat(service.getReservationById(7L, tourist).getTotalPrice()).isEqualByComparingTo("240.00");
    }

    @Test
    void createReservation_moreGuestsThanOccupancyAllows_throwsBadRequest() {
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));

        // maxOccupancy 2 x 1 room = 2 guests at most
        RoomReservationRequestDto request = new RoomReservationRequestDto(
                60L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 1, 3);

        assertThatThrownBy(() -> service.createReservation(request, tourist))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("guest");
        verify(roomReservationRepository, never()).save(any());
    }

    @Test
    void confirmReservationAfterPayment_archivedProperty_throwsBadRequest() {
        accommodation.setStatus(AccommodationStatus.ARCHIVED);
        RoomReservation pending = reservation(RoomReservationStatus.PENDING, tourist);
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(pending));
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> service.confirmReservationAfterPayment(7L))
                .isInstanceOf(BadRequestException.class);
        assertThat(pending.getStatus()).isEqualTo(RoomReservationStatus.PENDING);
        verify(roomReservationRepository, never()).save(any());
    }

    @Test
    void confirmReservationAfterPayment_pausedProperty_throwsBadRequest() {
        accommodation.setStatus(AccommodationStatus.TEMPORARILY_UNAVAILABLE);
        when(roomReservationRepository.findById(7L))
                .thenReturn(Optional.of(reservation(RoomReservationStatus.PENDING, tourist)));
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> service.confirmReservationAfterPayment(7L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("paused");
    }

    @Test
    void completeReservation_beforeCheckIn_throwsBadRequest() {
        when(roomReservationRepository.findById(7L))
                .thenReturn(Optional.of(reservation(RoomReservationStatus.CONFIRMED, tourist)));

        assertThatThrownBy(() -> service.completeReservation(7L, hotelOwner))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("check-in");
        verify(roomReservationRepository, never()).save(any());
    }

    @Test
    void cancelReservation_byTouristWithSucceededPayment_refundsPerPolicy() {
        RoomReservation confirmed = reservation(RoomReservationStatus.CONFIRMED, tourist);
        Payment paid = payment(PaymentStatus.SUCCEEDED, "240.00");
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(confirmed));
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.ROOM_RESERVATION, 7L))
                .thenReturn(List.of(paid));
        when(cancellationPolicy.resolveRefundPercent(confirmed.getCheckInDate())).thenReturn(50);

        service.cancelReservation(7L, tourist);

        verify(refundGateway).refund(paid, new BigDecimal("120.00"));
    }

    @Test
    void cancelReservation_byTouristInsideNoRefundWindow_doesNotRefund() {
        RoomReservation confirmed = reservation(RoomReservationStatus.CONFIRMED, tourist);
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(confirmed));
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.ROOM_RESERVATION, 7L))
                .thenReturn(List.of(payment(PaymentStatus.SUCCEEDED, "240.00")));
        when(cancellationPolicy.resolveRefundPercent(any())).thenReturn(0);

        service.cancelReservation(7L, tourist);

        verify(refundGateway, never()).refund(any(), any());
        assertThat(confirmed.getStatus()).isEqualTo(RoomReservationStatus.CANCELLED);
    }

    @Test
    void cancelReservation_byOwner_refundsInFullRegardlessOfPolicy() {
        Payment paid = payment(PaymentStatus.SUCCEEDED, "240.00");
        when(roomReservationRepository.findById(7L))
                .thenReturn(Optional.of(reservation(RoomReservationStatus.CONFIRMED, tourist)));
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.ROOM_RESERVATION, 7L))
                .thenReturn(List.of(paid));

        service.cancelReservation(7L, hotelOwner);

        verify(refundGateway).refund(paid, new BigDecimal("240.00"));
        verify(cancellationPolicy, never()).resolveRefundPercent(any());
    }

    @Test
    void cancelReservation_withUnpaidPendingPayment_marksPaymentCancelled() {
        Payment unpaid = payment(PaymentStatus.PENDING, "240.00");
        when(roomReservationRepository.findById(7L))
                .thenReturn(Optional.of(reservation(RoomReservationStatus.PENDING, tourist)));
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.ROOM_RESERVATION, 7L))
                .thenReturn(List.of(unpaid));

        service.cancelReservation(7L, tourist);

        verify(refundGateway).cancelUncompletedPayment(unpaid);
        verify(refundGateway, never()).refund(any(), any());
    }

    @Test
    void getCancellationPreview_forTourist_usesPolicyPercentOfPaidAmount() {
        RoomReservation confirmed = reservation(RoomReservationStatus.CONFIRMED, tourist);
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(confirmed));
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.ROOM_RESERVATION, 7L))
                .thenReturn(List.of(payment(PaymentStatus.SUCCEEDED, "240.00")));
        when(cancellationPolicy.resolveRefundPercent(confirmed.getCheckInDate())).thenReturn(50);
        when(cancellationPolicy.describeRule(confirmed.getCheckInDate())).thenReturn("rule");

        CancellationPreviewResponseDto preview = service.getCancellationPreview(7L, tourist);

        assertThat(preview.getRefundPercent()).isEqualTo(50);
        assertThat(preview.getRefundAmount()).isEqualByComparingTo("120.00");
        assertThat(preview.isHasPayment()).isTrue();
        assertThat(preview.getRuleText()).isEqualTo("rule");
    }

    @Test
    void getCancellationPreview_unpaidReservation_hasNoPaymentAndZeroRefund() {
        RoomReservation pending = reservation(RoomReservationStatus.PENDING, tourist);
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(pending));
        when(cancellationPolicy.resolveRefundPercent(any())).thenReturn(100);

        CancellationPreviewResponseDto preview = service.getCancellationPreview(7L, tourist);

        assertThat(preview.isHasPayment()).isFalse();
        assertThat(preview.getRefundAmount()).isEqualByComparingTo("0");
    }

    @Test
    void getCancellationPreview_byOwner_isFullRefund() {
        when(roomReservationRepository.findById(7L))
                .thenReturn(Optional.of(reservation(RoomReservationStatus.CONFIRMED, tourist)));
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.ROOM_RESERVATION, 7L))
                .thenReturn(List.of(payment(PaymentStatus.SUCCEEDED, "240.00")));

        CancellationPreviewResponseDto preview = service.getCancellationPreview(7L, hotelOwner);

        assertThat(preview.getRefundPercent()).isEqualTo(100);
        assertThat(preview.getRefundAmount()).isEqualByComparingTo("240.00");
    }

    @Test
    void cancelForAccommodation_cancelsHeldStaysRefundsInFullAndNotifies() {
        RoomReservation upcoming = reservation(RoomReservationStatus.CONFIRMED, tourist);
        RoomReservation over = reservation(RoomReservationStatus.CONFIRMED, tourist);
        over.setId(8L);
        over.setCheckInDate(LocalDate.now().minusDays(5));
        over.setCheckOutDate(LocalDate.now().minusDays(2));
        Payment paid = payment(PaymentStatus.SUCCEEDED, "240.00");
        when(roomReservationRepository.findByRoomAccommodationIdAndStatusIn(any(), any()))
                .thenReturn(List.of(upcoming, over));
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.ROOM_RESERVATION, 7L))
                .thenReturn(List.of(paid));

        int cancelled = service.cancelForAccommodation(50L,
                java.util.EnumSet.of(RoomReservationStatus.CONFIRMED), "the property was archived.");

        assertThat(cancelled).isEqualTo(1);
        assertThat(upcoming.getStatus()).isEqualTo(RoomReservationStatus.CANCELLED);
        assertThat(over.getStatus()).isEqualTo(RoomReservationStatus.CONFIRMED); // stay already over
        verify(refundGateway).refund(paid, new BigDecimal("240.00"));
        verify(notificationService).notify(eq(tourist), eq(NotificationType.ROOM_RESERVATION_CANCELLED),
                any(), argThat(m -> m.contains("archived")), eq("/reservations/mine"));
    }

}
