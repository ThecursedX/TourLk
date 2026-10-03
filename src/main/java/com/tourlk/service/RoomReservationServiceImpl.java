package com.tourlk.service;

import com.tourlk.dto.RoomReservationRequestDto;
import com.tourlk.dto.RoomReservationResponseDto;
import com.tourlk.dto.RoomSummaryDto;
import com.tourlk.entity.Accommodation;
import com.tourlk.entity.Booking;
import com.tourlk.entity.Room;
import com.tourlk.entity.RoomReservation;
import com.tourlk.entity.User;
import com.tourlk.enums.AccommodationStatus;
import com.tourlk.enums.BookingStatus;
import com.tourlk.enums.NotificationType;
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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomReservationServiceImpl implements RoomReservationService {

    private static final java.util.Set<RoomReservationStatus> TERMINAL_STATUSES =
            java.util.EnumSet.of(RoomReservationStatus.CANCELLED, RoomReservationStatus.COMPLETED);

    /** A reservation holds its rooms while PENDING (awaiting payment) as well as CONFIRMED. */
    static final java.util.Set<RoomReservationStatus> HOLDING_STATUSES =
            java.util.EnumSet.of(RoomReservationStatus.PENDING, RoomReservationStatus.CONFIRMED);

    private final RoomReservationRepository roomReservationRepository;
    private final RoomRepository roomRepository;
    private final AccommodationService accommodationService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public RoomReservationResponseDto createReservation(RoomReservationRequestDto request, User currentUser) {
        return doCreate(request.getRoomId(), request.getCheckInDate(), request.getCheckOutDate(),
                request.getNumberOfRooms(), currentUser, null);
    }

    @Override
    @Transactional
    public RoomReservationResponseDto createLinkedReservation(Booking booking, Long roomId, int numberOfRooms,
                                                              LocalDate checkIn, LocalDate checkOut) {
        return doCreate(roomId, checkIn, checkOut, numberOfRooms, booking.getTourist(), booking);
    }

    private RoomReservationResponseDto doCreate(Long roomId, LocalDate checkInDate, LocalDate checkOutDate,
                                                int numberOfRooms, User currentUser, Booking booking) {
        assertDateRange(checkInDate, checkOutDate);

        Room room = getLockedRoom(roomId);
        Accommodation accommodation = room.getAccommodation();

        if (accommodation.getStatus() == AccommodationStatus.TEMPORARILY_UNAVAILABLE) {
            throw new BadRequestException("This accommodation is temporarily unavailable for reservations");
        }
        // FULLY_BOOKED only means tonight is full; later dates may still have rooms,
        // so it falls through to the per-room availability check below.
        if (accommodation.getStatus() != AccommodationStatus.ACTIVE
                && accommodation.getStatus() != AccommodationStatus.FULLY_BOOKED) {
            throw new BadRequestException("This accommodation is not currently open for reservations");
        }

        assertAvailability(room, checkInDate, checkOutDate, numberOfRooms, 0L);

        RoomReservation reservation = RoomReservation.builder()
                .tourist(currentUser)
                .room(room)
                .checkInDate(checkInDate)
                .checkOutDate(checkOutDate)
                .numberOfRooms(numberOfRooms)
                .booking(booking)
                .status(RoomReservationStatus.PENDING)
                .build();

        RoomReservation saved = roomReservationRepository.save(reservation);
        notifyUser(accommodation.getOwner(), currentUser, NotificationType.ROOM_RESERVATION_REQUESTED,
                "New reservation request", currentUser.getName() + " requested " + saved.getNumberOfRooms()
                        + " x " + room.getRoomType() + " at " + accommodation.getName() + " from "
                        + saved.getCheckInDate() + " to " + saved.getCheckOutDate() + ".",
                "/accommodations/owner/reservations");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public RoomReservationResponseDto confirmReservation(Long id, User currentUser) {
        RoomReservation reservation = getEntity(id);
        assertNotPartOfActiveBooking(reservation, "confirmed");
        Room room = getLockedRoom(reservation.getRoom().getId());
        assertOwnerOrAdmin(room.getAccommodation(), currentUser);
        return doConfirm(reservation, room, currentUser);
    }

    @Override
    @Transactional
    public RoomReservationResponseDto confirmReservationAfterPayment(Long id) {
        RoomReservation reservation = getEntity(id);
        Room room = getLockedRoom(reservation.getRoom().getId());
        return doConfirm(reservation, room, null);
    }

    /** {@code actor} is the owner/admin who confirmed, or null when a successful payment confirmed it. */
    private RoomReservationResponseDto doConfirm(RoomReservation reservation, Room room, User actor) {
        assertStatus(reservation, RoomReservationStatus.PENDING, "confirmed");
        assertAvailability(room, reservation.getCheckInDate(), reservation.getCheckOutDate(),
                reservation.getNumberOfRooms(), reservation.getId());

        reservation.setStatus(RoomReservationStatus.CONFIRMED);
        RoomReservation saved = roomReservationRepository.save(reservation);
        accommodationService.refreshAvailabilityStatus(room.getAccommodation().getId());
        Accommodation accommodation = room.getAccommodation();
        notifyUser(saved.getTourist(), actor, NotificationType.ROOM_RESERVATION_CONFIRMED, "Reservation confirmed",
                "Your stay at " + accommodation.getName() + " from " + saved.getCheckInDate() + " to "
                        + saved.getCheckOutDate() + " is confirmed.", "/reservations/mine");
        notifyUser(accommodation.getOwner(), actor, NotificationType.ROOM_RESERVATION_CONFIRMED,
                "Reservation confirmed", saved.getNumberOfRooms() + " x " + room.getRoomType() + " booked from "
                        + saved.getCheckInDate() + " to " + saved.getCheckOutDate() + ".",
                "/accommodations/owner/reservations");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public RoomReservationResponseDto cancelReservation(Long id, User currentUser) {
        RoomReservation reservation = getEntity(id);
        assertTouristOrOwnerOrAdmin(reservation, currentUser);
        assertNotPartOfActiveBooking(reservation, "cancelled");

        if (TERMINAL_STATUSES.contains(reservation.getStatus())) {
            throw new InvalidStatusTransitionException("This reservation is already " + reservation.getStatus());
        }

        reservation.setStatus(RoomReservationStatus.CANCELLED);
        RoomReservation saved = roomReservationRepository.save(reservation);
        accommodationService.refreshAvailabilityStatus(reservation.getRoom().getAccommodation().getId());
        Accommodation accommodation = saved.getRoom().getAccommodation();
        boolean byTourist = saved.getTourist().getId().equals(currentUser.getId());
        notifyUser(byTourist ? accommodation.getOwner() : saved.getTourist(), currentUser,
                NotificationType.ROOM_RESERVATION_CANCELLED, "Reservation cancelled",
                "The reservation at " + accommodation.getName() + " from " + saved.getCheckInDate() + " to "
                        + saved.getCheckOutDate() + " was cancelled.",
                byTourist ? "/accommodations/owner/reservations" : "/reservations/mine");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void cancelLinkedToBooking(Long bookingId) {
        for (RoomReservation reservation : roomReservationRepository.findByBookingId(bookingId)) {
            if (TERMINAL_STATUSES.contains(reservation.getStatus())) {
                continue;
            }
            reservation.setStatus(RoomReservationStatus.CANCELLED);
            roomReservationRepository.save(reservation);
            Accommodation accommodation = reservation.getRoom().getAccommodation();
            accommodationService.refreshAvailabilityStatus(accommodation.getId());
            notifyUser(accommodation.getOwner(), null, NotificationType.ROOM_RESERVATION_CANCELLED,
                    "Reservation cancelled", reservation.getNumberOfRooms() + " x " + reservation.getRoom().getRoomType()
                            + " from " + reservation.getCheckInDate() + " to " + reservation.getCheckOutDate()
                            + " was cancelled because the package booking it belonged to was cancelled.",
                    "/accommodations/owner/reservations");
        }
    }

    @Override
    @Transactional
    public void confirmLinkedAfterPayment(Long bookingId) {
        for (RoomReservation reservation : roomReservationRepository.findByBookingId(bookingId)) {
            if (reservation.getStatus() != RoomReservationStatus.PENDING) {
                continue;
            }
            try {
                doConfirm(reservation, getLockedRoom(reservation.getRoom().getId()), null);
            } catch (RuntimeException ex) {
                log.error("Booking {} was paid but confirming its room reservation {} failed — needs manual review",
                        bookingId, reservation.getId(), ex);
            }
        }
    }

    @Override
    @Transactional
    public void moveLinkedToDates(Long bookingId, LocalDate checkIn, LocalDate checkOut) {
        for (RoomReservation reservation : roomReservationRepository.findByBookingId(bookingId)) {
            if (TERMINAL_STATUSES.contains(reservation.getStatus())) {
                continue;
            }
            Room room = getLockedRoom(reservation.getRoom().getId());
            assertAvailability(room, checkIn, checkOut, reservation.getNumberOfRooms(), reservation.getId());
            reservation.setCheckInDate(checkIn);
            reservation.setCheckOutDate(checkOut);
            roomReservationRepository.save(reservation);
        }
    }

    @Override
    public List<RoomReservationResponseDto> getLinkedToBooking(Long bookingId) {
        return roomReservationRepository.findByBookingId(bookingId).stream().map(this::toResponse).toList();
    }

    /** Add-ons are managed through their package booking while it is still active. */
    private void assertNotPartOfActiveBooking(RoomReservation reservation, String action) {
        Booking booking = reservation.getBooking();
        if (booking != null && !BookingStatus.TERMINAL.contains(booking.getStatus())) {
            throw new BadRequestException("This reservation is part of the package booking #" + booking.getId()
                    + " (" + booking.getTourPackage().getTitle() + ") and can't be " + action
                    + " on its own — manage the package booking instead.");
        }
    }

    @Override
    @Transactional
    public boolean expireUnpaidReservation(Long id) {
        RoomReservation reservation = roomReservationRepository.findById(id).orElse(null);
        if (reservation == null || reservation.getStatus() != RoomReservationStatus.PENDING) {
            return false;
        }
        reservation.setStatus(RoomReservationStatus.CANCELLED);
        roomReservationRepository.save(reservation);
        Accommodation accommodation = reservation.getRoom().getAccommodation();
        notificationService.notify(reservation.getTourist(), NotificationType.ROOM_RESERVATION_CANCELLED,
                "Reservation expired", "Your reservation at " + accommodation.getName() + " from "
                        + reservation.getCheckInDate() + " to " + reservation.getCheckOutDate()
                        + " was cancelled because it was not paid in time.", "/reservations/mine");
        return true;
    }

    @Override
    @Transactional
    public RoomReservationResponseDto completeReservation(Long id, User currentUser) {
        RoomReservation reservation = getEntity(id);
        assertOwnerOrAdmin(reservation.getRoom().getAccommodation(), currentUser);
        assertStatus(reservation, RoomReservationStatus.CONFIRMED, "completed");

        reservation.setStatus(RoomReservationStatus.COMPLETED);
        RoomReservation saved = roomReservationRepository.save(reservation);
        accommodationService.refreshAvailabilityStatus(reservation.getRoom().getAccommodation().getId());
        notifyUser(saved.getTourist(), currentUser, NotificationType.ROOM_RESERVATION_COMPLETED,
                "Stay completed", "Your stay at " + saved.getRoom().getAccommodation().getName()
                        + " is complete. How was it? Leave a review.", "/reservations/mine");
        return toResponse(saved);
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

    private void assertAvailability(Room room, LocalDate checkIn, LocalDate checkOut, int numberOfRooms,
                                    Long excludeReservationId) {
        int alreadyReserved = roomReservationRepository.sumReservedRoomsOverlapping(
                room.getId(), HOLDING_STATUSES, checkIn, checkOut, excludeReservationId);

        if (alreadyReserved + numberOfRooms > room.getTotalRooms()) {
            int remaining = Math.max(0, room.getTotalRooms() - alreadyReserved);
            throw new RoomUnavailableException(
                    "Only " + remaining + " room(s) of this type available for " + checkIn + " to " + checkOut);
        }
    }

    /** In-app notice; skipped when the recipient is the person who just did it. */
    private void notifyUser(User recipient, User actor, NotificationType type, String title, String message,
                            String link) {
        if (actor == null || !recipient.getId().equals(actor.getId())) {
            notificationService.notify(recipient, type, title, message, link);
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
                .bookingId(reservation.getBooking() == null ? null : reservation.getBooking().getId())
                .packageTitle(reservation.getBooking() == null ? null
                        : reservation.getBooking().getTourPackage().getTitle())
                .createdAt(reservation.getCreatedAt())
                .build();
    }

}
