package com.tourlk.service;

import com.tourlk.dto.CancellationPreviewResponseDto;
import com.tourlk.dto.RoomReservationRequestDto;
import com.tourlk.dto.RoomReservationResponseDto;
import com.tourlk.dto.RoomSummaryDto;
import com.tourlk.entity.Accommodation;
import com.tourlk.entity.Booking;
import com.tourlk.entity.Payment;
import com.tourlk.entity.Room;
import com.tourlk.entity.RoomReservation;
import com.tourlk.entity.User;
import com.tourlk.enums.AccommodationStatus;
import com.tourlk.enums.BookingStatus;
import com.tourlk.enums.NotificationType;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

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
    private final PaymentRepository paymentRepository;
    private final RefundGateway refundGateway;
    private final RoomCancellationPolicy cancellationPolicy;

    @Override
    @Transactional
    public RoomReservationResponseDto createReservation(RoomReservationRequestDto request, User currentUser) {
        return doCreate(request.getRoomId(), request.getCheckInDate(), request.getCheckOutDate(),
                request.getNumberOfRooms(), request.getNumberOfGuests(), currentUser, null);
    }

    @Override
    @Transactional
    public RoomReservationResponseDto createLinkedReservation(Booking booking, Long roomId, int numberOfRooms,
                                                              LocalDate checkIn, LocalDate checkOut) {
        return doCreate(roomId, checkIn, checkOut, numberOfRooms, null, booking.getTourist(), booking);
    }

    private RoomReservationResponseDto doCreate(Long roomId, LocalDate checkInDate, LocalDate checkOutDate,
                                                int numberOfRooms, Integer numberOfGuests, User currentUser,
                                                Booking booking) {
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

        // Linked add-ons carry no guest count (null); only direct reservations are checked.
        if (numberOfGuests != null && numberOfGuests > room.getMaxOccupancy() * numberOfRooms) {
            throw new BadRequestException("This room type sleeps at most " + room.getMaxOccupancy()
                    + " guest(s) per room, so " + numberOfRooms + " room(s) can host "
                    + room.getMaxOccupancy() * numberOfRooms + " guest(s), not " + numberOfGuests);
        }

        assertAvailability(room, checkInDate, checkOutDate, numberOfRooms, 0L);

        RoomReservation reservation = RoomReservation.builder()
                .tourist(currentUser)
                .room(room)
                .checkInDate(checkInDate)
                .checkOutDate(checkOutDate)
                .numberOfRooms(numberOfRooms)
                .numberOfGuests(numberOfGuests)
                .totalPrice(priceOf(room.getPricePerNight(), checkInDate, checkOutDate, numberOfRooms))
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
    public RoomReservationResponseDto confirmReservationAfterPayment(Long id) {
        RoomReservation reservation = getEntity(id);
        Room room = getLockedRoom(reservation.getRoom().getId());
        return doConfirm(reservation, room);
    }

    /** Only payment confirms a reservation, so there is never an acting user to skip notifying. */
    private RoomReservationResponseDto doConfirm(RoomReservation reservation, Room room) {
        assertStatus(reservation, RoomReservationStatus.PENDING, "confirmed");
        assertOpenForPayment(room.getAccommodation());
        assertAvailability(room, reservation.getCheckInDate(), reservation.getCheckOutDate(),
                reservation.getNumberOfRooms(), reservation.getId());

        reservation.setStatus(RoomReservationStatus.CONFIRMED);
        RoomReservation saved = roomReservationRepository.save(reservation);
        accommodationService.refreshAvailabilityStatus(room.getAccommodation().getId());
        Accommodation accommodation = room.getAccommodation();
        notifyUser(saved.getTourist(), null, NotificationType.ROOM_RESERVATION_CONFIRMED, "Reservation confirmed",
                "Your stay at " + accommodation.getName() + " from " + saved.getCheckInDate() + " to "
                        + saved.getCheckOutDate() + " is confirmed.", "/reservations/mine");
        notifyUser(accommodation.getOwner(), null, NotificationType.ROOM_RESERVATION_CONFIRMED,
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
        boolean byTourist = reservation.getTourist().getId().equals(currentUser.getId());
        // A tourist manages add-ons through the package booking; the property may still pull its own room.
        if (byTourist || !isOwnerOrAdmin(reservation, currentUser)) {
            assertNotPartOfActiveBooking(reservation, "cancelled");
        }

        if (TERMINAL_STATUSES.contains(reservation.getStatus())) {
            throw new InvalidStatusTransitionException("This reservation is already " + reservation.getStatus());
        }

        boolean linkedAddOn = isLinkedToActiveBooking(reservation);
        if (linkedAddOn) {
            refundLinkedShare(reservation);
        } else {
            applyRefund(reservation, refundPercentFor(reservation, byTourist));
        }

        reservation.setStatus(RoomReservationStatus.CANCELLED);
        RoomReservation saved = roomReservationRepository.save(reservation);
        accommodationService.refreshAvailabilityStatus(reservation.getRoom().getAccommodation().getId());
        Accommodation accommodation = saved.getRoom().getAccommodation();
        String message = linkedAddOn
                ? "The hotel cancelled your room at " + accommodation.getName() + " (" + saved.getCheckInDate()
                        + " to " + saved.getCheckOutDate() + "). The rest of your package booking is unaffected."
                : "The reservation at " + accommodation.getName() + " from " + saved.getCheckInDate() + " to "
                        + saved.getCheckOutDate() + " was cancelled.";
        notifyUser(byTourist ? accommodation.getOwner() : saved.getTourist(), currentUser,
                NotificationType.ROOM_RESERVATION_CANCELLED, "Reservation cancelled", message,
                byTourist ? "/accommodations/owner/reservations" : "/reservations/mine");
        return toResponse(saved);
    }

    @Override
    public CancellationPreviewResponseDto getCancellationPreview(Long id, User currentUser) {
        RoomReservation reservation = getEntity(id);
        assertTouristOrOwnerOrAdmin(reservation, currentUser);
        boolean byTourist = reservation.getTourist().getId().equals(currentUser.getId());
        if (byTourist || !isOwnerOrAdmin(reservation, currentUser)) {
            assertNotPartOfActiveBooking(reservation, "cancelled");
        }

        int refundPercent = refundPercentFor(reservation, byTourist);
        Payment succeeded = findSucceededPayment(reservation);

        return CancellationPreviewResponseDto.builder()
                .refundPercent(refundPercent)
                .refundAmount(succeeded == null ? BigDecimal.ZERO : refundAmountFor(succeeded, refundPercent))
                .ruleText(byTourist ? cancellationPolicy.describeRule(reservation.getCheckInDate())
                        : "Cancelled by the property: full refund.")
                .hasPayment(succeeded != null)
                .build();
    }

    @Override
    @Transactional
    public int cancelForAccommodation(Long accommodationId, Set<RoomReservationStatus> statuses, String reason) {
        LocalDate today = LocalDate.now();
        int cancelled = 0;
        for (RoomReservation reservation
                : roomReservationRepository.findByRoomAccommodationIdAndStatusIn(accommodationId, statuses)) {
            boolean stayOver = !reservation.getCheckOutDate().isAfter(today);
            // A finished stay that was paid for stays as it is; one that was never paid is just stale.
            if (stayOver && reservation.getStatus() != RoomReservationStatus.PENDING) {
                continue;
            }
            if (isLinkedToActiveBooking(reservation)) {
                if (!stayOver) {
                    refundLinkedShare(reservation);
                }
            } else {
                applyRefund(reservation, 100);
            }
            reservation.setStatus(RoomReservationStatus.CANCELLED);
            roomReservationRepository.save(reservation);
            cancelled++;
            notificationService.notify(reservation.getTourist(), NotificationType.ROOM_RESERVATION_CANCELLED,
                    "Reservation cancelled", "Your reservation at "
                            + reservation.getRoom().getAccommodation().getName() + " from "
                            + reservation.getCheckInDate() + " to " + reservation.getCheckOutDate()
                            + " was cancelled: " + reason + " Any payment is being refunded in full.",
                    "/reservations/mine");
        }
        return cancelled;
    }

    private boolean isOwnerOrAdmin(RoomReservation reservation, User user) {
        return user.getRole() == Role.ADMIN
                || reservation.getRoom().getAccommodation().getOwner().getId().equals(user.getId());
    }

    private boolean isLinkedToActiveBooking(RoomReservation reservation) {
        Booking booking = reservation.getBooking();
        return booking != null && !BookingStatus.TERMINAL.contains(booking.getStatus());
    }

    /**
     * A linked add-on has no payment of its own; its share was paid inside the package booking's payment.
     * {@link Payment} records a single refund total and {@link RefundGateway#refund} moves the whole payment to
     * REFUNDED, so a partial refund now would block (or double-count against) the refund of the booking itself.
     * Until payments can track several partial refunds, the share is refunded by hand: the tourist and every
     * admin are told. A booking with no SUCCEEDED payment has nothing to refund, so nobody is notified.
     */
    private void refundLinkedShare(RoomReservation reservation) {
        Booking booking = reservation.getBooking();
        boolean paid = paymentRepository.findByPayableTypeAndPayableId(PayableType.BOOKING, booking.getId())
                .stream().anyMatch(p -> p.getStatus() == PaymentStatus.SUCCEEDED);
        if (!paid) {
            return;
        }
        Accommodation accommodation = reservation.getRoom().getAccommodation();
        BigDecimal share = reservation.getTotalPrice() != null ? reservation.getTotalPrice()
                : priceOf(reservation.getRoom().getPricePerNight(), reservation.getCheckInDate(),
                        reservation.getCheckOutDate(), reservation.getNumberOfRooms());
        notificationService.notify(reservation.getTourist(), NotificationType.PAYMENT_REFUNDED,
                "Refund for cancelled room", "Your room at " + accommodation.getName() + " in package booking #"
                        + booking.getId() + " was cancelled. Its share (" + share + ") will be refunded to you "
                        + "manually; our team has been notified.", "/payments/mine");
        notificationService.notifyAdmins(NotificationType.PAYMENT_REFUNDED, "Manual refund needed",
                "Room reservation #" + reservation.getId() + " (" + accommodation.getName()
                        + ") in package booking #" + booking.getId() + " was cancelled. Refund " + share
                        + " to " + reservation.getTourist().getName() + " manually.", "/payments");
    }

    /** 100% unless the tourist is the one cancelling (then the check-in-date policy applies). */
    private int refundPercentFor(RoomReservation reservation, boolean byTourist) {
        return byTourist ? cancellationPolicy.resolveRefundPercent(reservation.getCheckInDate()) : 100;
    }

    /**
     * A SUCCEEDED payment is refunded by {@code refundPercent} (0% leaves it SUCCEEDED); one that never
     * completed is marked CANCELLED. Mirrors {@code BookingServiceImpl#applyCancellationRefund}.
     */
    private void applyRefund(RoomReservation reservation, int refundPercent) {
        for (Payment payment : paymentRepository
                .findByPayableTypeAndPayableId(PayableType.ROOM_RESERVATION, reservation.getId())) {
            if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                if (refundPercent > 0) {
                    refundGateway.refund(payment, refundAmountFor(payment, refundPercent));
                }
            } else if (payment.getStatus() == PaymentStatus.PENDING) {
                refundGateway.cancelUncompletedPayment(payment);
            }
        }
    }

    private Payment findSucceededPayment(RoomReservation reservation) {
        return paymentRepository.findByPayableTypeAndPayableId(PayableType.ROOM_RESERVATION, reservation.getId())
                .stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal refundAmountFor(Payment payment, int refundPercent) {
        return payment.getAmount()
                .multiply(BigDecimal.valueOf(refundPercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
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
                doConfirm(reservation, getLockedRoom(reservation.getRoom().getId()));
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
        if (LocalDate.now().isBefore(reservation.getCheckInDate())) {
            throw new BadRequestException("A reservation can only be completed on or after its check-in date ("
                    + reservation.getCheckInDate() + ")");
        }

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

    /** A property that is archived, inactive or paused can't take (or finish taking) a payment. */
    private void assertOpenForPayment(Accommodation accommodation) {
        if (accommodation.getStatus() != AccommodationStatus.ACTIVE
                && accommodation.getStatus() != AccommodationStatus.FULLY_BOOKED) {
            throw new BadRequestException(accommodation.getStatus() == AccommodationStatus.TEMPORARILY_UNAVAILABLE
                    ? "This accommodation has paused reservations, so this one can't be paid until it resumes"
                    : "This accommodation is no longer open for reservations, so this one can't be paid");
        }
    }

    private BigDecimal priceOf(BigDecimal pricePerNight, LocalDate checkIn, LocalDate checkOut, int numberOfRooms) {
        return pricePerNight
                .multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(checkIn, checkOut)))
                .multiply(BigDecimal.valueOf(numberOfRooms));
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

    /** First non-blank image of the room, else of the property, else null. */
    private static String firstImage(java.util.List<String> roomImages, java.util.List<String> propertyImages) {
        for (java.util.List<String> images : java.util.Arrays.asList(roomImages, propertyImages)) {
            if (images == null) continue;
            for (String url : images) {
                if (url != null && !url.isBlank()) return url;
            }
        }
        return null;
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
                .accommodationStatus(accommodation.getStatus())
                .coverImageUrl(firstImage(room.getImageUrls(), accommodation.getImageUrls()))
                .build();

        return RoomReservationResponseDto.builder()
                .id(reservation.getId())
                .room(roomSummary)
                .touristId(reservation.getTourist().getId())
                .touristName(reservation.getTourist().getName())
                .checkInDate(reservation.getCheckInDate())
                .checkOutDate(reservation.getCheckOutDate())
                .numberOfRooms(reservation.getNumberOfRooms())
                .numberOfGuests(reservation.getNumberOfGuests())
                // Frozen price; legacy rows (null) fall back to the live room price.
                .totalPrice(reservation.getTotalPrice() != null ? reservation.getTotalPrice()
                        : priceOf(room.getPricePerNight(), reservation.getCheckInDate(),
                                reservation.getCheckOutDate(), reservation.getNumberOfRooms()))
                .status(reservation.getStatus())
                .bookingId(reservation.getBooking() == null ? null : reservation.getBooking().getId())
                .packageTitle(reservation.getBooking() == null ? null
                        : reservation.getBooking().getTourPackage().getTitle())
                .createdAt(reservation.getCreatedAt())
                .build();
    }

}
