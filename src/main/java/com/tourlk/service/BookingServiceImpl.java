package com.tourlk.service;

import com.tourlk.dto.BookingPackageSummaryDto;
import com.tourlk.dto.BookingRequestDto;
import com.tourlk.dto.BookingResponseDto;
import com.tourlk.dto.RescheduleRequestDto;
import com.tourlk.entity.Booking;
import com.tourlk.entity.TourPackage;
import com.tourlk.entity.User;
import com.tourlk.enums.BookingStatus;
import com.tourlk.enums.PackageStatus;
import com.tourlk.enums.Role;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.CapacityExceededException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.BookingRepository;
import com.tourlk.repo.TourPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Capacity is enforced with pessimistic (not optimistic) locking: two
 * tourists booking the last spot on the same date is a real, frequent
 * race, and the right behaviour is for the second request to wait its
 * turn and then either succeed or get a clean "no capacity" answer — not
 * to fail with an optimistic-lock conflict and force the client to
 * blindly retry against a resource (remaining capacity) that changes
 * often. See {@code getLockedPackage} below for the locking mechanism.
 */
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    /** Statuses that currently occupy a seat on their travelDate. RESCHEDULED counts — see {@link BookingStatus}. */
    private static final Set<BookingStatus> CAPACITY_HOLDING_STATUSES =
            EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.RESCHEDULED);

    private static final Set<BookingStatus> RESCHEDULABLE_STATUSES =
            EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.RESCHEDULED);

    private static final Set<BookingStatus> TERMINAL_STATUSES =
            EnumSet.of(BookingStatus.COMPLETED, BookingStatus.CANCELLED);

    private final BookingRepository bookingRepository;
    private final TourPackageRepository tourPackageRepository;

    @Override
    @Transactional
    public BookingResponseDto createBooking(BookingRequestDto request, User currentUser) {
        TourPackage tourPackage = getLockedPackage(request.getTourPackageId());

        if (tourPackage.getStatus() != PackageStatus.ACTIVE) {
            throw new BadRequestException("This package is not currently open for bookings");
        }

        assertCapacityAvailable(tourPackage, request.getTravelDate(), request.getNumberOfTravelers());

        Booking booking = Booking.builder()
                .tourist(currentUser)
                .tourPackage(tourPackage)
                .travelDate(request.getTravelDate())
                .numberOfTravelers(request.getNumberOfTravelers())
                .specialRequests(request.getSpecialRequests())
                .status(BookingStatus.PENDING)
                .build();

        return toResponse(bookingRepository.save(booking));
    }

    @Override
    @Transactional
    public BookingResponseDto confirmBooking(Long id) {
        Booking booking = getEntity(id);
        assertStatus(booking, BookingStatus.PENDING, "confirmed");

        TourPackage tourPackage = getLockedPackage(booking.getTourPackage().getId());
        assertCapacityAvailable(tourPackage, booking.getTravelDate(), booking.getNumberOfTravelers());

        booking.setStatus(BookingStatus.CONFIRMED);
        return toResponse(bookingRepository.save(booking));
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

        booking.setStatusBeforeReschedule(booking.getStatus());
        booking.setRequestedTravelDate(request.getNewTravelDate());
        booking.setStatus(BookingStatus.RESCHEDULE_REQUESTED);

        return toResponse(bookingRepository.save(booking));
    }

    @Override
    @Transactional
    public BookingResponseDto approveReschedule(Long id) {
        Booking booking = getEntity(id);
        assertStatus(booking, BookingStatus.RESCHEDULE_REQUESTED, "approved");

        TourPackage tourPackage = getLockedPackage(booking.getTourPackage().getId());
        assertCapacityAvailable(tourPackage, booking.getRequestedTravelDate(), booking.getNumberOfTravelers());

        booking.setPreviousTravelDate(booking.getTravelDate());
        booking.setTravelDate(booking.getRequestedTravelDate());
        booking.setRequestedTravelDate(null);
        booking.setStatusBeforeReschedule(null);
        booking.setStatus(BookingStatus.RESCHEDULED);

        return toResponse(bookingRepository.save(booking));
    }

    @Override
    @Transactional
    public BookingResponseDto rejectReschedule(Long id) {
        Booking booking = getEntity(id);
        assertStatus(booking, BookingStatus.RESCHEDULE_REQUESTED, "rejected");

        booking.setStatus(booking.getStatusBeforeReschedule());
        booking.setRequestedTravelDate(null);
        booking.setStatusBeforeReschedule(null);

        return toResponse(bookingRepository.save(booking));
    }

    @Override
    @Transactional
    public BookingResponseDto cancelBooking(Long id, User currentUser) {
        Booking booking = getEntity(id);
        assertOwnerOrAdmin(booking, currentUser);

        if (TERMINAL_STATUSES.contains(booking.getStatus())) {
            throw new InvalidStatusTransitionException("This booking is already " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.CANCELLED);
        return toResponse(bookingRepository.save(booking));
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
        return toResponse(bookingRepository.save(booking));
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
    public List<BookingResponseDto> getBookingsByPackage(Long packageId) {
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

    private void assertCapacityAvailable(TourPackage tourPackage, LocalDate travelDate, int travelers) {
        int alreadyBooked = bookingRepository.sumTravelersByPackageAndDateAndStatusIn(
                tourPackage.getId(), travelDate, CAPACITY_HOLDING_STATUSES);

        if (alreadyBooked + travelers > tourPackage.getMaxCapacity()) {
            int remaining = Math.max(0, tourPackage.getMaxCapacity() - alreadyBooked);
            throw new CapacityExceededException(
                    "Only " + remaining + " spot(s) left for " + travelDate + " on this package");
        }
    }

    private Booking getEntity(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));
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

    private void assertStatus(Booking booking, BookingStatus required, String action) {
        if (booking.getStatus() != required) {
            throw new InvalidStatusTransitionException(
                    "Only " + required + " bookings can be " + action + ", but this booking is "
                            + booking.getStatus());
        }
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
                .specialRequests(booking.getSpecialRequests())
                .status(booking.getStatus())
                .previousTravelDate(booking.getPreviousTravelDate())
                .requestedTravelDate(booking.getRequestedTravelDate())
                .createdAt(booking.getCreatedAt())
                .build();
    }

}
