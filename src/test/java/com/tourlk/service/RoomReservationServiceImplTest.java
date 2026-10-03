package com.tourlk.service;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.eq;
import com.tourlk.enums.NotificationType;
import com.tourlk.dto.RoomReservationRequestDto;
import com.tourlk.dto.RoomReservationResponseDto;
import com.tourlk.entity.Accommodation;
import com.tourlk.entity.TourPackage;
import com.tourlk.entity.Booking;
import com.tourlk.entity.Destination;
import com.tourlk.entity.Room;
import com.tourlk.entity.RoomReservation;
import com.tourlk.entity.User;
import com.tourlk.enums.AccommodationStatus;
import com.tourlk.enums.BookingStatus;
import com.tourlk.enums.DestinationStatus;
import com.tourlk.enums.Role;
import com.tourlk.enums.RoomReservationStatus;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.InvalidDateRangeException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.exception.RoomUnavailableException;
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
                60L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 1);
        RoomReservationResponseDto result = service.createReservation(request, tourist);

        assertThat(result.getStatus()).isEqualTo(RoomReservationStatus.PENDING);
        assertThat(result.getTouristId()).isEqualTo(1L);
        assertThat(result.getRoom().getAccommodationId()).isEqualTo(50L);
    }

    @Test
    void createReservation_checkOutNotAfterCheckIn_throwsInvalidDateRange() {
        LocalDate sameDay = LocalDate.now().plusDays(10);
        RoomReservationRequestDto request = new RoomReservationRequestDto(60L, sameDay, sameDay, 1);

        assertThatThrownBy(() -> service.createReservation(request, tourist))
                .isInstanceOf(InvalidDateRangeException.class);
        verify(roomReservationRepository, never()).save(any());
    }

    @Test
    void createReservation_accommodationNotActive_throwsBadRequest() {
        accommodation.setStatus(AccommodationStatus.INACTIVE);
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));

        RoomReservationRequestDto request = new RoomReservationRequestDto(
                60L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 1);

        assertThatThrownBy(() -> service.createReservation(request, tourist))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createReservation_temporarilyUnavailable_throwsBadRequestAndDoesNotSave() {
        accommodation.setStatus(AccommodationStatus.TEMPORARILY_UNAVAILABLE);
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));

        RoomReservationRequestDto request = new RoomReservationRequestDto(
                60L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 1);

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
                60L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 1);

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
                60L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 2);

        assertThat(service.createReservation(request, tourist).getNumberOfRooms()).isEqualTo(2);
    }

    @Test
    void createReservation_oneRoomOverRemainingInventory_throwsRoomUnavailable() {
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));
        when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(3);

        RoomReservationRequestDto request = new RoomReservationRequestDto(
                60L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 3);

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

        service.createReservation(new RoomReservationRequestDto(60L, in, out, 1), tourist);

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

        assertThatThrownBy(() -> service.confirmReservation(7L, hotelOwner))
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

        service.confirmReservation(7L, hotelOwner);

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
                60L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 1); // 5 + 1 > 5

        assertThatThrownBy(() -> service.createReservation(request, tourist))
                .isInstanceOf(RoomUnavailableException.class);
        verify(roomReservationRepository, never()).save(any());
    }

    @Test
    void createReservation_roomNotFound_throwsResourceNotFound() {
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.empty());

        RoomReservationRequestDto request = new RoomReservationRequestDto(
                60L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 1);

        assertThatThrownBy(() -> service.createReservation(request, tourist))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // confirmReservation — ownership + status
    // ------------------------------------------------------------------

    @Test
    void confirmReservation_byAccommodationOwner_confirms() {
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(reservation(RoomReservationStatus.PENDING, tourist)));
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));
        when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(0);
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomReservationResponseDto result = service.confirmReservation(7L, hotelOwner);

        assertThat(result.getStatus()).isEqualTo(RoomReservationStatus.CONFIRMED);
    }

    @Test
    void confirmReservation_byUnrelatedUser_throwsAccessDenied() {
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(reservation(RoomReservationStatus.PENDING, tourist)));
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> service.confirmReservation(7L, stranger))
                .isInstanceOf(AccessDeniedException.class);
        verify(roomReservationRepository, never()).save(any());
    }

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

        assertThatThrownBy(() -> service.confirmReservation(7L, hotelOwner))
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
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(reservation(RoomReservationStatus.CONFIRMED, tourist)));
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
                60L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 1), tourist);

        verify(notificationService).notify(eq(hotelOwner), eq(NotificationType.ROOM_RESERVATION_REQUESTED), any(), any(),
                eq("/accommodations/owner/reservations"));
    }

    @Test
    void confirmReservation_byOwner_notifiesOnlyTheTourist() {
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(reservation(RoomReservationStatus.PENDING, tourist)));
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));
        when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(0);
        when(roomReservationRepository.save(any(RoomReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.confirmReservation(7L, hotelOwner);

        verify(notificationService).notify(eq(tourist), eq(NotificationType.ROOM_RESERVATION_CONFIRMED), any(), any(), eq("/reservations/mine"));
        verify(notificationService, never()).notify(eq(hotelOwner), any(), any(), any(), any());
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
        when(roomReservationRepository.findById(7L)).thenReturn(Optional.of(reservation(RoomReservationStatus.CONFIRMED, tourist)));
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
    void cancelReservation_linkedToActiveBooking_isRejectedWithPointerToTheBooking() {
        when(roomReservationRepository.findById(7L))
                .thenReturn(Optional.of(linkedReservation(RoomReservationStatus.PENDING, BookingStatus.PENDING)));

        assertThatThrownBy(() -> service.cancelReservation(7L, hotelOwner))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("package booking #5");
        verify(roomReservationRepository, never()).save(any());
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

}
