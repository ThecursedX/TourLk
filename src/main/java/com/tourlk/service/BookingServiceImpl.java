package com.tourlk.service;

import com.tourlk.dto.AddOnRoomSelectionDto;
import com.tourlk.dto.BookingPackageSummaryDto;
import com.tourlk.dto.BookingRequestDto;
import com.tourlk.dto.BookingResponseDto;
import com.tourlk.dto.CancellationPreviewResponseDto;
import com.tourlk.dto.RescheduleRequestDto;
import com.tourlk.dto.RoomReservationResponseDto;
import com.tourlk.dto.VehicleHireResponseDto;
import com.tourlk.entity.Booking;
import com.tourlk.entity.Destination;
import com.tourlk.entity.PackageAddOn;
import com.tourlk.entity.PackageDeparture;
import com.tourlk.entity.Payment;
import com.tourlk.entity.TourPackage;
import com.tourlk.entity.User;
import com.tourlk.enums.BookingStatus;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.PackageStatus;
import com.tourlk.enums.PayableType;
import com.tourlk.enums.PaymentStatus;
import com.tourlk.enums.Role;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.CapacityExceededException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.BookingRepository;
import com.tourlk.repo.PackageAddOnRepository;
import com.tourlk.repo.PackageDepartureRepository;
import com.tourlk.repo.PaymentRepository;
import com.tourlk.repo.TourPackageRepository;
import com.tourlk.util.AddOnDates;
import com.tourlk.util.ClosureWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Capacity is enforced with pessimistic (not optimistic) locking: two
 * tourists booking the last spot on the same date is a real, frequent
 * race, and the right behaviour is for the second request to wait its
 * turn and then either succeed or get a clean "no capacity" answer — not
 * to fail with an optimistic-lock conflict and force the client to
 * blindly retry against a resource (remaining capacity) that changes
 * often. See {@code getLockedPackage} below for the locking mechanism.
 * <p>
 * Packages with scheduled {@link PackageDeparture}s only accept new travel
 * dates (on create and reschedule) that match one of them, and each
 * departure's seatsTotal is that date's seat limit. Packages with no
 * departures accept any future date, limited by the package maxCapacity.
 */
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    /** Statuses that currently occupy a seat on their travelDate. RESCHEDULED counts — see {@link BookingStatus}. */
    private static final Set<BookingStatus> CAPACITY_HOLDING_STATUSES = BookingStatus.CAPACITY_HOLDING;

    private static final Set<BookingStatus> RESCHEDULABLE_STATUSES =
            EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.RESCHEDULED);

    private static final Set<BookingStatus> TERMINAL_STATUSES = BookingStatus.TERMINAL;

    private final BookingRepository bookingRepository;
    private final TourPackageRepository tourPackageRepository;
    private final PackageDepartureRepository departureRepository;
    private final PaymentRepository paymentRepository;
    private final RefundGateway refundGateway;
    private final CancellationPolicy cancellationPolicy;
    private final NotificationService notificationService;
    private final PackageAddOnRepository addOnRepository;
    private final RoomReservationService roomReservationService;
    private final VehicleHireService vehicleHireService;

    @Override
    @Transactional
    public BookingResponseDto createBooking(BookingRequestDto request, User currentUser) {
        TourPackage tourPackage = getLockedPackage(request.getTourPackageId());

        if (tourPackage.getStatus() != PackageStatus.ACTIVE) {
            throw new BadRequestException("This package is not currently open for bookings");
        }

        assertDestinationOpenForTrip(tourPackage, request.getTravelDate());
        assertCapacityAvailable(tourPackage, request.getTravelDate(), request.getNumberOfTravelers(), true);

        List<AddOnRoomSelectionDto> roomPicks =
                request.getAddOnRooms() == null ? List.of() : request.getAddOnRooms();
        List<Long> vehiclePicks =
                request.getAddOnVehicleIds() == null ? List.of() : request.getAddOnVehicleIds();
        assertAddOnsAttached(tourPackage, roomPicks, vehiclePicks);

        BigDecimal packageSubtotal =
                tourPackage.getPrice().multiply(BigDecimal.valueOf(request.getNumberOfTravelers()));

        Booking booking = Booking.builder()
                .tourist(currentUser)
                .tourPackage(tourPackage)
                .travelDate(request.getTravelDate())
                .numberOfTravelers(request.getNumberOfTravelers())
                .totalPrice(packageSubtotal)
                .packageSubtotal(packageSubtotal)
                .roomsSubtotal(BigDecimal.ZERO)
                .vehiclesSubtotal(BigDecimal.ZERO)
                .specialRequests(request.getSpecialRequests())
                .status(BookingStatus.PENDING)
                .build();

        booking = bookingRepository.save(booking);

        // Add-ons are created in this same transaction: if any is unavailable its exception rolls back
        // the whole booking.
        if (!roomPicks.isEmpty() || !vehiclePicks.isEmpty()) {
            LocalDate travelDate = request.getTravelDate();
            int days = tourPackage.getDurationDays();
            LocalDate checkIn = AddOnDates.roomCheckIn(travelDate);
            LocalDate checkOut = AddOnDates.roomCheckOut(travelDate, days);
            long nights = ChronoUnit.DAYS.between(checkIn, checkOut);

            BigDecimal roomsSubtotal = BigDecimal.ZERO;
            for (AddOnRoomSelectionDto pick : roomPicks) {
                RoomReservationResponseDto reservation = roomReservationService.createLinkedReservation(
                        booking, pick.getRoomId(), pick.getNumberOfRooms(), checkIn, checkOut);
                roomsSubtotal = roomsSubtotal.add(reservation.getRoom().getPricePerNight()
                        .multiply(BigDecimal.valueOf(nights))
                        .multiply(BigDecimal.valueOf(pick.getNumberOfRooms())));
            }

            BigDecimal vehiclesSubtotal = BigDecimal.ZERO;
            String pickup = tourPackage.getDestination().getName() + " (package pickup)";
            for (Long vehicleId : vehiclePicks) {
                VehicleHireResponseDto hire = vehicleHireService.createLinkedHire(booking, vehicleId,
                        AddOnDates.vehicleStart(travelDate), AddOnDates.vehicleEnd(travelDate, days), pickup);
                vehiclesSubtotal = vehiclesSubtotal.add(hire.getTotalPrice());
            }

            booking.setRoomsSubtotal(roomsSubtotal);
            booking.setVehiclesSubtotal(vehiclesSubtotal);
            booking.setTotalPrice(packageSubtotal.add(roomsSubtotal).add(vehiclesSubtotal));
            booking = bookingRepository.save(booking);
        }

        notificationService.notify(tourPackage.getCreatedBy(), NotificationType.BOOKING_CREATED,
                "New booking on " + tourPackage.getTitle(),
                currentUser.getName() + " booked " + booking.getNumberOfTravelers() + " traveler(s) for "
                        + booking.getTravelDate(),
                "/bookings/" + booking.getId());

        return toResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponseDto confirmBooking(Long id, User currentUser) {
        Booking booking = getEntity(id);
        assertPackageOwnerOrAdmin(booking, currentUser);
        return confirm(booking);
    }

    @Override
    @Transactional
    public void confirmBookingAfterPayment(Long id) {
        Booking booking = getEntity(id);
        // The guide/admin may already have confirmed it before the tourist paid.
        if (booking.getStatus() == BookingStatus.PENDING) {
            confirm(booking);
        }
        // Paying the booking pays for its rooms/vehicles too.
        if (CAPACITY_HOLDING_STATUSES.contains(booking.getStatus())) {
            roomReservationService.confirmLinkedAfterPayment(booking.getId());
            vehicleHireService.confirmLinkedAfterPayment(booking.getId());
        }
    }

    private BookingResponseDto confirm(Booking booking) {
        assertStatus(booking, BookingStatus.PENDING, "confirmed");

        TourPackage tourPackage = getLockedPackage(booking.getTourPackage().getId());
        // Not forced onto a departure: the date was already accepted at
        // creation, possibly before this package had any departures.
        assertCapacityAvailable(tourPackage, booking.getTravelDate(), booking.getNumberOfTravelers(), false);

        booking.setStatus(BookingStatus.CONFIRMED);
        booking = bookingRepository.save(booking);

        notifyTourist(booking, NotificationType.BOOKING_CONFIRMED, "Booking confirmed",
                "Your booking for " + booking.getTourPackage().getTitle() + " has been confirmed");

        return toResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponseDto requestReschedule(Long id, RescheduleRequestDto request, User currentUser) {
        Booking booking = getEntity(id);
        assertOwner(booking, currentUser);

        if (!RESCHEDULABLE_STATUSES.contains(booking.getStatus())) {
            throw new InvalidStatusTransitionException(
                    "A reschedule can only be requested while a booking is PENDING, CONFIRMED or RESCHEDULED, not "
                            + booking.getStatus());
        }

        assertDestinationOpenForTrip(booking.getTourPackage(), request.getNewTravelDate());
        // Early, unlocked feedback for the tourist; approveReschedule re-checks under the package lock.
        seatLimitFor(booking.getTourPackage(), request.getNewTravelDate(), true);

        booking.setStatusBeforeReschedule(booking.getStatus());
        booking.setRequestedTravelDate(request.getNewTravelDate());
        booking.setStatus(BookingStatus.RESCHEDULE_REQUESTED);
        booking = bookingRepository.save(booking);

        notifyTourist(booking, NotificationType.BOOKING_RESCHEDULE_REQUESTED, "Reschedule requested",
                "Your request to reschedule your booking for " + booking.getTourPackage().getTitle()
                        + " to " + booking.getRequestedTravelDate() + " has been submitted");
        notifyPackageOwner(booking, NotificationType.BOOKING_RESCHEDULE_REQUESTED, "Reschedule requested",
                booking.getTourist().getName() + " asked to move their booking for "
                        + booking.getTourPackage().getTitle() + " to " + booking.getRequestedTravelDate());

        return toResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponseDto approveReschedule(Long id) {
        Booking booking = getEntity(id);
        assertStatus(booking, BookingStatus.RESCHEDULE_REQUESTED, "approved");

        TourPackage tourPackage = getLockedPackage(booking.getTourPackage().getId());
        // A closure may have been created since the request was made.
        assertDestinationOpenForTrip(tourPackage, booking.getRequestedTravelDate());
        // Re-checked under the lock: the departure may have been removed since the request.
        assertCapacityAvailable(tourPackage, booking.getRequestedTravelDate(), booking.getNumberOfTravelers(), true);

        booking.setPreviousTravelDate(booking.getTravelDate());
        booking.setTravelDate(booking.getRequestedTravelDate());
        booking.setRequestedTravelDate(null);
        booking.setStatusBeforeReschedule(null);
        booking.setStatus(BookingStatus.RESCHEDULED);
        booking = bookingRepository.save(booking);

        // Rooms/vehicles follow the trip; fails (rolling the reschedule back) if they are taken on the new dates.
        int days = tourPackage.getDurationDays();
        roomReservationService.moveLinkedToDates(booking.getId(), AddOnDates.roomCheckIn(booking.getTravelDate()),
                AddOnDates.roomCheckOut(booking.getTravelDate(), days));
        vehicleHireService.moveLinkedToDates(booking.getId(), AddOnDates.vehicleStart(booking.getTravelDate()),
                AddOnDates.vehicleEnd(booking.getTravelDate(), days));

        notifyTourist(booking, NotificationType.BOOKING_RESCHEDULE_APPROVED, "Reschedule approved",
                "Your reschedule request for " + booking.getTourPackage().getTitle()
                        + " has been approved — new date: " + booking.getTravelDate());

        return toResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponseDto rejectReschedule(Long id) {
        Booking booking = getEntity(id);
        assertStatus(booking, BookingStatus.RESCHEDULE_REQUESTED, "rejected");

        booking.setStatus(booking.getStatusBeforeReschedule());
        booking.setRequestedTravelDate(null);
        booking.setStatusBeforeReschedule(null);
        booking = bookingRepository.save(booking);

        notifyTourist(booking, NotificationType.BOOKING_RESCHEDULE_REJECTED, "Reschedule rejected",
                "Your reschedule request for " + booking.getTourPackage().getTitle() + " was rejected");

        return toResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponseDto cancelBooking(Long id, User currentUser) {
        Booking booking = getEntity(id);
        assertOwnerOrAdmin(booking, currentUser);

        if (TERMINAL_STATUSES.contains(booking.getStatus())) {
            throw new InvalidStatusTransitionException("This booking is already " + booking.getStatus());
        }

        applyCancellationRefund(booking);

        booking.setStatus(BookingStatus.CANCELLED);
        booking = bookingRepository.save(booking);
        cancelLinkedAddOns(booking);

        notifyTourist(booking, NotificationType.BOOKING_CANCELLED, "Booking cancelled",
                "Your booking for " + booking.getTourPackage().getTitle() + " has been cancelled");
        if (booking.getTourist().getId().equals(currentUser.getId())) {
            notifyPackageOwner(booking, NotificationType.BOOKING_CANCELLED, "Booking cancelled",
                    booking.getTourist().getName() + " cancelled their booking for "
                            + booking.getTourPackage().getTitle());
        }

        return toResponse(booking);
    }

    @Override
    public CancellationPreviewResponseDto getCancellationPreview(Long id, User currentUser) {
        Booking booking = getEntity(id);
        assertOwnerOrAdmin(booking, currentUser);

        int refundPercent = cancellationPolicy.resolveRefundPercent(booking.getTravelDate());
        Payment succeededPayment = findSucceededPayment(booking);
        BigDecimal refundAmount = succeededPayment == null
                ? BigDecimal.ZERO
                : refundAmountFor(succeededPayment, refundPercent);

        return CancellationPreviewResponseDto.builder()
                .refundPercent(refundPercent)
                .refundAmount(refundAmount)
                .ruleText(cancellationPolicy.describeRule(booking.getTravelDate()))
                .hasPayment(succeededPayment != null)
                .build();
    }

    @Override
    @Transactional
    public BookingResponseDto rejectBooking(Long id, String reason, User currentUser) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A rejection reason is required");
        }
        Booking booking = getEntity(id);
        assertPackageOwnerOrAdmin(booking, currentUser);
        assertStatus(booking, BookingStatus.PENDING, "rejected");

        applyCancellationRefund(booking);

        booking.setStatus(BookingStatus.REJECTED);
        booking.setRejectionReason(reason.trim());
        booking = bookingRepository.save(booking);
        cancelLinkedAddOns(booking);

        notifyTourist(booking, NotificationType.BOOKING_REJECTED, "Booking rejected",
                "Your booking for " + booking.getTourPackage().getTitle() + " was not approved. Reason: "
                        + booking.getRejectionReason());

        return toResponse(booking);
    }

    @Override
    @Transactional
    public boolean expireUnpaidBooking(Long id) {
        Booking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.PENDING) {
            return false;
        }
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setRejectionReason("Not paid in time");
        booking = bookingRepository.save(booking);
        cancelLinkedAddOns(booking);
        notifyTourist(booking, NotificationType.BOOKING_CANCELLED, "Booking expired",
                "Your booking for " + booking.getTourPackage().getTitle()
                        + " was cancelled because it was not paid in time");
        return true;
    }

    private void cancelLinkedAddOns(Booking booking) {
        roomReservationService.cancelLinkedToBooking(booking.getId());
        vehicleHireService.cancelLinkedToBooking(booking.getId());
    }

    @Override
    @Transactional
    public BookingResponseDto completeBooking(Long id) {
        Booking booking = getEntity(id);

        if (!CAPACITY_HOLDING_STATUSES.contains(booking.getStatus())) {
            throw new InvalidStatusTransitionException(
                    "Only CONFIRMED or RESCHEDULED bookings can be completed, not " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.COMPLETED);
        booking = bookingRepository.save(booking);

        notifyTourist(booking, NotificationType.BOOKING_COMPLETED, "Booking completed",
                "Your booking for " + booking.getTourPackage().getTitle() + " is now complete — leave a review!");

        return toResponse(booking);
    }

    @Override
    public BookingResponseDto getBookingById(Long id, User currentUser) {
        Booking booking = getEntity(id);
        boolean isTourist = booking.getTourist().getId().equals(currentUser.getId());
        boolean isPackageOwner = booking.getTourPackage().getCreatedBy().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        if (!isTourist && !isPackageOwner && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to view this booking");
        }
        return toResponse(booking);
    }

    @Override
    public List<BookingResponseDto> getBookingsByTourist(Long touristId) {
        return bookingRepository.findByTouristId(touristId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<BookingResponseDto> getBookingsByPackage(Long packageId, User currentUser) {
        TourPackage tourPackage = tourPackageRepository.findById(packageId)
                .orElseThrow(() -> new ResourceNotFoundException("Tour package not found with id: " + packageId));

        boolean isOwner = tourPackage.getCreatedBy().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to view bookings for this package");
        }

        return bookingRepository.findByTourPackageId(packageId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<BookingResponseDto> getBookingsForGuide(User guide) {
        return bookingRepository.findByTourPackageCreatedById(guide.getId(),
                        Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Every chosen room/vehicle must actually be attached to this package, and chosen at most once. */
    private void assertAddOnsAttached(TourPackage tourPackage, List<AddOnRoomSelectionDto> roomPicks,
                                      List<Long> vehiclePicks) {
        if (roomPicks.isEmpty() && vehiclePicks.isEmpty()) {
            return;
        }
        Set<Long> attachedRooms = new HashSet<>();
        Set<Long> attachedVehicles = new HashSet<>();
        for (PackageAddOn addOn : addOnRepository.findByTourPackageId(tourPackage.getId())) {
            if (addOn.getRoom() != null) {
                attachedRooms.add(addOn.getRoom().getId());
            }
            if (addOn.getVehicle() != null) {
                attachedVehicles.add(addOn.getVehicle().getId());
            }
        }

        Set<Long> seenRooms = new HashSet<>();
        for (AddOnRoomSelectionDto pick : roomPicks) {
            if (!attachedRooms.contains(pick.getRoomId())) {
                throw new BadRequestException("Room " + pick.getRoomId() + " is not an add-on of this package");
            }
            if (!seenRooms.add(pick.getRoomId())) {
                throw new BadRequestException("Each room can only be added once (use the number of rooms)");
            }
        }
        Set<Long> seenVehicles = new HashSet<>();
        for (Long vehicleId : vehiclePicks) {
            if (!attachedVehicles.contains(vehicleId)) {
                throw new BadRequestException("Vehicle " + vehicleId + " is not an add-on of this package");
            }
            if (!seenVehicles.add(vehicleId)) {
                throw new BadRequestException("Each vehicle can only be added once");
            }
        }
    }

    /**
     * Locks the package row for the rest of this transaction, serializing
     * every create/confirm/approve-reschedule capacity check for that
     * package into one at a time. We lock the package rather than the
     * booking-count query because a locked aggregate query only locks the
     * rows it matches — with zero existing bookings for a date (the very
     * first booking request), there's nothing for it to lock, so two
     * concurrent transactions could both count 0 and both proceed. Locking
     * the package closes that gap by making "check capacity, then act on
     * it" atomic per package, at the cost of serializing bookings across
     * different dates of the same package too — an acceptable trade-off
     * for correctness over throughput here.
     */
    private TourPackage getLockedPackage(Long packageId) {
        return tourPackageRepository.findByIdForUpdate(packageId)
                .orElseThrow(() -> new ResourceNotFoundException("Tour package not found with id: " + packageId));
    }

    /**
     * Rejects a trip whose date range (travelDate .. travelDate + durationDays - 1) overlaps the
     * closure window of the package's destination. Tolerates a package without a destination.
     */
    private void assertDestinationOpenForTrip(TourPackage tourPackage, LocalDate travelDate) {
        Destination destination = tourPackage.getDestination();
        ClosureWindow.of(destination, LocalDate.now())
                .filter(window -> window.overlaps(travelDate, tourPackage.getDurationDays()))
                .ifPresent(window -> {
                    throw new BadRequestException(destination.getName() + " is closed " + window.describe()
                            + ", so this package can't be booked for those dates.");
                });
    }

    private void assertCapacityAvailable(TourPackage tourPackage, LocalDate travelDate, int travelers,
                                         boolean mustMatchDeparture) {
        int seatLimit = seatLimitFor(tourPackage, travelDate, mustMatchDeparture);
        int alreadyBooked = bookingRepository.sumTravelersByPackageAndDateAndStatusIn(
                tourPackage.getId(), travelDate, CAPACITY_HOLDING_STATUSES);

        if (alreadyBooked + travelers > seatLimit) {
            int remaining = Math.max(0, seatLimit - alreadyBooked);
            throw new CapacityExceededException(
                    "Only " + remaining + " spot(s) left for " + travelDate + " on this package");
        }
    }

    /**
     * The seat limit for a package on a date: the matching departure's
     * seatsTotal, or the package maxCapacity when there's no departure on
     * that date. With {@code mustMatchDeparture}, a date that isn't one of
     * the package's departures is rejected — but only if the package has
     * departures at all, so packages without any keep the free-date rule.
     */
    private int seatLimitFor(TourPackage tourPackage, LocalDate travelDate, boolean mustMatchDeparture) {
        Optional<PackageDeparture> departure =
                departureRepository.findByTourPackageIdAndDepartureDate(tourPackage.getId(), travelDate);
        if (departure.isPresent()) {
            return departure.get().getSeatsTotal();
        }
        if (mustMatchDeparture && departureRepository.existsByTourPackageId(tourPackage.getId())) {
            throw new BadRequestException("This package has no departure on " + travelDate
                    + " — please choose one of its scheduled departure dates");
        }
        return tourPackage.getMaxCapacity();
    }

    private Booking getEntity(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));
    }

    private void notifyTourist(Booking booking, NotificationType type, String title, String message) {
        notificationService.notify(booking.getTourist(), type, title, message, "/bookings/" + booking.getId());
    }

    private void notifyPackageOwner(Booking booking, NotificationType type, String title, String message) {
        notificationService.notify(booking.getTourPackage().getCreatedBy(), type, title, message,
                "/bookings/" + booking.getId());
    }

    private void assertOwner(Booking booking, User currentUser) {
        if (!booking.getTourist().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have permission to manage this booking");
        }
    }

    private void assertOwnerOrAdmin(Booking booking, User currentUser) {
        boolean isOwner = booking.getTourist().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to manage this booking");
        }
    }

    private void assertPackageOwnerOrAdmin(Booking booking, User currentUser) {
        boolean isPackageOwner = booking.getTourPackage().getCreatedBy().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isPackageOwner && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to manage this booking");
        }
    }

    /**
     * Applied on cancel/reject: a SUCCEEDED payment is refunded by whatever
     * percentage the {@link CancellationPolicy} gives for how close
     * travelDate is (0% simply leaves it SUCCEEDED — nothing to refund); a
     * payment that never completed (still PENDING) is marked CANCELLED.
     */
    private void applyCancellationRefund(Booking booking) {
        for (Payment payment : paymentRepository.findByPayableTypeAndPayableId(PayableType.BOOKING, booking.getId())) {
            if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                int refundPercent = cancellationPolicy.resolveRefundPercent(booking.getTravelDate());
                if (refundPercent > 0) {
                    refundGateway.refund(payment, refundAmountFor(payment, refundPercent));
                }
            } else if (payment.getStatus() == PaymentStatus.PENDING) {
                refundGateway.cancelUncompletedPayment(payment);
            }
        }
    }

    private Payment findSucceededPayment(Booking booking) {
        return paymentRepository.findByPayableTypeAndPayableId(PayableType.BOOKING, booking.getId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal refundAmountFor(Payment payment, int refundPercent) {
        return payment.getAmount()
                .multiply(BigDecimal.valueOf(refundPercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private void assertStatus(Booking booking, BookingStatus required, String action) {
        if (booking.getStatus() != required) {
            throw new InvalidStatusTransitionException(
                    "Only " + required + " bookings can be " + action + ", but this booking is "
                            + booking.getStatus());
        }
    }

    /**
     * The stored price, or — for rows created before totalPrice existed —
     * the live package price * travelers (the old behaviour).
     */
    private BigDecimal totalPriceOf(Booking booking) {
        if (booking.getTotalPrice() != null) {
            return booking.getTotalPrice();
        }
        return booking.getTourPackage().getPrice().multiply(BigDecimal.valueOf(booking.getNumberOfTravelers()));
    }

    private BookingResponseDto toResponse(Booking booking) {
        TourPackage tourPackage = booking.getTourPackage();

        BookingPackageSummaryDto packageSummary = BookingPackageSummaryDto.builder()
                .id(tourPackage.getId())
                .title(tourPackage.getTitle())
                .destination(tourPackage.getDestination().getName())
                .price(tourPackage.getPrice())
                .build();

        return BookingResponseDto.builder()
                .id(booking.getId())
                .tourPackage(packageSummary)
                .touristId(booking.getTourist().getId())
                .touristName(booking.getTourist().getName())
                .travelDate(booking.getTravelDate())
                .numberOfTravelers(booking.getNumberOfTravelers())
                .totalPrice(totalPriceOf(booking))
                .packageSubtotal(booking.getPackageSubtotal() != null
                        ? booking.getPackageSubtotal() : totalPriceOf(booking))
                .roomsSubtotal(booking.getRoomsSubtotal() != null ? booking.getRoomsSubtotal() : BigDecimal.ZERO)
                .vehiclesSubtotal(booking.getVehiclesSubtotal() != null
                        ? booking.getVehiclesSubtotal() : BigDecimal.ZERO)
                .roomReservations(roomReservationService.getLinkedToBooking(booking.getId()))
                .vehicleHires(vehicleHireService.getLinkedToBooking(booking.getId()))
                .specialRequests(booking.getSpecialRequests())
                .status(booking.getStatus())
                .previousTravelDate(booking.getPreviousTravelDate())
                .requestedTravelDate(booking.getRequestedTravelDate())
                .rejectionReason(booking.getRejectionReason())
                .paid(findSucceededPayment(booking) != null)
                .createdAt(booking.getCreatedAt())
                .build();
    }

}
