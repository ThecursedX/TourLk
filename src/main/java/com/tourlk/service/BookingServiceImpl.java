package com.tourlk.service;

import com.tourlk.dto.BookingPackageSummaryDto;
import com.tourlk.dto.BookingRequestDto;
import com.tourlk.dto.BookingResponseDto;
import com.tourlk.dto.CancellationPreviewResponseDto;
import com.tourlk.dto.RescheduleRequestDto;
import com.tourlk.entity.Booking;
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
import com.tourlk.repo.PackageDepartureRepository;
import com.tourlk.repo.PaymentRepository;
import com.tourlk.repo.TourPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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

    @Override
    @Transactional
    public BookingResponseDto createBooking(BookingRequestDto request, User currentUser) {
        TourPackage tourPackage = getLockedPackage(request.getTourPackageId());

        if (tourPackage.getStatus() != PackageStatus.ACTIVE) {
            throw new BadRequestException("This package is not currently open for bookings");
        }

        assertCapacityAvailable(tourPackage, request.getTravelDate(), request.getNumberOfTravelers(), true);

        Booking booking = Booking.builder()
                .tourist(currentUser)
                .tourPackage(tourPackage)
                .travelDate(request.getTravelDate())
                .numberOfTravelers(request.getNumberOfTravelers())
                .totalPrice(tourPackage.getPrice().multiply(BigDecimal.valueOf(request.getNumberOfTravelers())))
                .specialRequests(request.getSpecialRequests())
                .status(BookingStatus.PENDING)
                .build();

        booking = bookingRepository.save(booking);

        notificationService.notify(tourPackage.getCreatedBy(), NotificationType.BOOKING_CREATED,
                "New booking on " + tourPackage.getTitle(),
                currentUser.getName() + " booked " + booking.getNumberOfTravelers() + " traveler(s) for "
                        + booking.getTravelDate(),
                "/bookings/" + booking.getId());

        return toResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponseDto confirmBooking(Long id) {
        Booking booking = getEntity(id);
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

        // Early, unlocked feedback for the tourist; approveReschedule re-checks under the package lock.
        seatLimitFor(booking.getTourPackage(), request.getNewTravelDate(), true);

        booking.setStatusBeforeReschedule(booking.getStatus());
        booking.setRequestedTravelDate(request.getNewTravelDate());
        booking.setStatus(BookingStatus.RESCHEDULE_REQUESTED);
        booking = bookingRepository.save(booking);

        notifyTourist(booking, NotificationType.BOOKING_RESCHEDULE_REQUESTED, "Reschedule requested",
                "Your request to reschedule your booking for " + booking.getTourPackage().getTitle()
                        + " to " + booking.getRequestedTravelDate() + " has been submitted");

        return toResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponseDto approveReschedule(Long id) {
        Booking booking = getEntity(id);
        assertStatus(booking, BookingStatus.RESCHEDULE_REQUESTED, "approved");

        TourPackage tourPackage = getLockedPackage(booking.getTourPackage().getId());
        // Re-checked under the lock: the departure may have been removed since the request.
        assertCapacityAvailable(tourPackage, booking.getRequestedTravelDate(), booking.getNumberOfTravelers(), true);

        booking.setPreviousTravelDate(booking.getTravelDate());
        booking.setTravelDate(booking.getRequestedTravelDate());
        booking.setRequestedTravelDate(null);
        booking.setStatusBeforeReschedule(null);
        booking.setStatus(BookingStatus.RESCHEDULED);
        booking = bookingRepository.save(booking);

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

        notifyTourist(booking, NotificationType.BOOKING_CANCELLED, "Booking cancelled",
                "Your booking for " + booking.getTourPackage().getTitle() + " has been cancelled");

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

        notifyTourist(booking, NotificationType.BOOKING_REJECTED, "Booking rejected",
                "Your booking for " + booking.getTourPackage().getTitle() + " was not approved. Reason: "
                        + booking.getRejectionReason());

        return toResponse(booking);
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
        assertOwnerOrAdmin(booking, currentUser);
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
                .specialRequests(booking.getSpecialRequests())
                .status(booking.getStatus())
                .previousTravelDate(booking.getPreviousTravelDate())
                .requestedTravelDate(booking.getRequestedTravelDate())
                .rejectionReason(booking.getRejectionReason())
                .createdAt(booking.getCreatedAt())
                .build();
    }

}
