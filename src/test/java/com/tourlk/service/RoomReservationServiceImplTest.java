package com.tourlk.service;

import com.tourlk.dto.RoomReservationRequestDto;
import com.tourlk.dto.RoomReservationResponseDto;
import com.tourlk.entity.Accommodation;
import com.tourlk.entity.Destination;
import com.tourlk.entity.Room;
import com.tourlk.entity.RoomReservation;
import com.tourlk.entity.User;
import com.tourlk.enums.AccommodationStatus;
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
                        .id(1L).name("Galle").region("Southern Province").status(DestinationStatus.ACTIVE).build())
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
        when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any())).thenReturn(0);
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
    void createReservation_notEnoughRoomsLeft_throwsRoomUnavailable() {
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(room));
        when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any())).thenReturn(5);

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
        when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any())).thenReturn(0);
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
        when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any())).thenReturn(0);
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
}
