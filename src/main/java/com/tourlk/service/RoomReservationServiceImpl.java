package com.tourlk.service;

import com.tourlk.dto.RoomReservationRequestDto;
import com.tourlk.dto.RoomReservationResponseDto;
import com.tourlk.dto.RoomSummaryDto;
import com.tourlk.entity.Accommodation;
import com.tourlk.entity.Room;
import com.tourlk.entity.RoomReservation;
import com.tourlk.entity.User;
import com.tourlk.enums.AccommodationStatus;
import com.tourlk.enums.Role;
import com.tourlk.enums.RoomReservationStatus;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.InvalidDateRangeException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.exception.RoomUnavailableException;
import com.tourlk.repo.RoomRepository;
import com.tourlk.repo.RoomReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Availability is enforced with pessimistic locking for the same reason
 * as Booking's capacity check: two tourists reserving the last unit of a
 * room type for overlapping dates is a real, frequent race, and the
 * right behaviour is for the second request to wait and then get a
 * correct answer, not to fail on an optimistic-lock conflict and force a
 * blind retry against a number (remaining rooms) that changes often.
 * See {@code getLockedRoom} below for the locking mechanism.
 */
@Service
@RequiredArgsConstructor
public class RoomReservationServiceImpl implements RoomReservationService {

    private static final java.util.Set<RoomReservationStatus> TERMINAL_STATUSES =
            java.util.EnumSet.of(RoomReservationStatus.CANCELLED, RoomReservationStatus.COMPLETED);

    private final RoomReservationRepository roomReservationRepository;
    private final RoomRepository roomRepository;

    @Override
    @Transactional
    public RoomReservationResponseDto createReservation(RoomReservationRequestDto request, User currentUser) {
        assertDateRange(request.getCheckInDate(), request.getCheckOutDate());

        Room room = getLockedRoom(request.getRoomId());
        Accommodation accommodation = room.getAccommodation();

        if (accommodation.getStatus() != AccommodationStatus.ACTIVE) {
            throw new BadRequestException("This accommodation is not currently open for reservations");
        }

        assertAvailability(room, request.getCheckInDate(), request.getCheckOutDate(), request.getNumberOfRooms());

        RoomReservation reservation = RoomReservation.builder()
                .tourist(currentUser)
                .room(room)
                .checkInDate(request.getCheckInDate())
                .checkOutDate(request.getCheckOutDate())
                .numberOfRooms(request.getNumberOfRooms())
                .status(RoomReservationStatus.PENDING)
                .build();

        return toResponse(roomReservationRepository.save(reservation));
    }

    @Override
    @Transactional
    public RoomReservationResponseDto confirmReservation(Long id, User currentUser) {
        RoomReservation reservation = getEntity(id);
        Room room = getLockedRoom(reservation.getRoom().getId());
        assertOwnerOrAdmin(room.getAccommodation(), currentUser);
        return doConfirm(reservation, room);
    }

    @Override
    @Transactional
    public RoomReservationResponseDto confirmReservationAfterPayment(Long id) {
        RoomReservation reservation = getEntity(id);
        Room room = getLockedRoom(reservation.getRoom().getId());
        return doConfirm(reservation, room);
    }

    private RoomReservationResponseDto doConfirm(RoomReservation reservation, Room room) {
        assertStatus(reservation, RoomReservationStatus.PENDING, "confirmed");
        assertAvailability(room, reservation.getCheckInDate(), reservation.getCheckOutDate(),
                reservation.getNumberOfRooms());

        reservation.setStatus(RoomReservationStatus.CONFIRMED);
        return toResponse(roomReservationRepository.save(reservation));
    }

    @Override
    @Transactional
    public RoomReservationResponseDto cancelReservation(Long id, User currentUser) {
        RoomReservation reservation = getEntity(id);
        assertTouristOrOwnerOrAdmin(reservation, currentUser);

        if (TERMINAL_STATUSES.contains(reservation.getStatus())) {
            throw new InvalidStatusTransitionException("This reservation is already " + reservation.getStatus());
        }

        reservation.setStatus(RoomReservationStatus.CANCELLED);
        return toResponse(roomReservationRepository.save(reservation));
    }

    @Override
    @Transactional
    public RoomReservationResponseDto completeReservation(Long id, User currentUser) {
        RoomReservation reservation = getEntity(id);
        assertOwnerOrAdmin(reservation.getRoom().getAccommodation(), currentUser);
        assertStatus(reservation, RoomReservationStatus.CONFIRMED, "completed");

        reservation.setStatus(RoomReservationStatus.COMPLETED);
        return toResponse(roomReservationRepository.save(reservation));
    }

    @Override
    public RoomReservationResponseDto getReservationById(Long id, User currentUser) {
        RoomReservation reservation = getEntity(id);
        assertTouristOrOwnerOrAdmin(reservation, currentUser);
        return toResponse(reservation);
    }

    @Override
    public List<RoomReservationResponseDto> getReservationsByTourist(Long touristId) {
        return roomReservationRepository.findByTouristId(touristId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<RoomReservationResponseDto> getReservationsByRoom(Long roomId, User currentUser) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + roomId));
        assertOwnerOrAdmin(room.getAccommodation(), currentUser);

        return roomReservationRepository.findByRoomId(roomId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Locks the room row for the rest of this transaction, serializing
     * every create/confirm availability check for that room into one at a
     * time. See {@code RoomRepository#findByIdForUpdate} for why the room
     * row is locked instead of the overlap-count query.
     */
    private Room getLockedRoom(Long roomId) {
        return roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + roomId));
    }

    private void assertAvailability(Room room, LocalDate checkIn, LocalDate checkOut, int numberOfRooms) {
        int alreadyReserved = roomReservationRepository.sumReservedRoomsOverlapping(
                room.getId(), RoomReservationStatus.CONFIRMED, checkIn, checkOut);

        if (alreadyReserved + numberOfRooms > room.getTotalRooms()) {
            int remaining = Math.max(0, room.getTotalRooms() - alreadyReserved);
            throw new RoomUnavailableException(
                    "Only " + remaining + " room(s) of this type available for " + checkIn + " to " + checkOut);
        }
    }

    private void assertDateRange(LocalDate checkIn, LocalDate checkOut) {
        if (checkOut == null || checkIn == null || !checkOut.isAfter(checkIn)) {
            throw new InvalidDateRangeException("Check-out date must be after check-in date");
        }
    }

    private RoomReservation getEntity(Long id) {
        return roomReservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));
    }

    private void assertOwnerOrAdmin(Accommodation accommodation, User currentUser) {
        boolean isOwner = accommodation.getOwner().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to manage this property's reservations");
        }
    }

    private void assertTouristOrOwnerOrAdmin(RoomReservation reservation, User currentUser) {
        boolean isTourist = reservation.getTourist().getId().equals(currentUser.getId());
        boolean isOwner = reservation.getRoom().getAccommodation().getOwner().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isTourist && !isOwner && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to manage this reservation");
        }
    }

    private void assertStatus(RoomReservation reservation, RoomReservationStatus required, String action) {
        if (reservation.getStatus() != required) {
            throw new InvalidStatusTransitionException(
                    "Only " + required + " reservations can be " + action + ", but this reservation is "
                            + reservation.getStatus());
        }
    }

    private RoomReservationResponseDto toResponse(RoomReservation reservation) {
        Room room = reservation.getRoom();
        Accommodation accommodation = room.getAccommodation();

        RoomSummaryDto roomSummary = RoomSummaryDto.builder()
                .id(room.getId())
                .roomType(room.getRoomType())
                .pricePerNight(room.getPricePerNight())
                .accommodationId(accommodation.getId())
                .accommodationName(accommodation.getName())
                .accommodationLocation(accommodation.getLocation().getName())
                .build();

        return RoomReservationResponseDto.builder()
                .id(reservation.getId())
                .room(roomSummary)
                .touristId(reservation.getTourist().getId())
                .touristName(reservation.getTourist().getName())
                .checkInDate(reservation.getCheckInDate())
                .checkOutDate(reservation.getCheckOutDate())
                .numberOfRooms(reservation.getNumberOfRooms())
                .status(reservation.getStatus())
                .createdAt(reservation.getCreatedAt())
                .build();
    }

}
