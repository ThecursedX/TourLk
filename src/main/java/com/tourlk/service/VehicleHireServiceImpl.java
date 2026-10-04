package com.tourlk.service;

import com.tourlk.dto.BookedDateRangeDto;
import com.tourlk.dto.VehicleHireRequestDto;
import com.tourlk.dto.VehicleHireResponseDto;
import com.tourlk.dto.VehicleSummaryDto;
import com.tourlk.entity.Booking;
import com.tourlk.entity.User;
import com.tourlk.entity.Vehicle;
import com.tourlk.entity.VehicleHire;
import com.tourlk.enums.BookingStatus;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.Role;
import com.tourlk.enums.VehicleHireStatus;
import com.tourlk.enums.VehicleStatus;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.InvalidDateRangeException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.exception.VehicleUnavailableException;
import com.tourlk.repo.VehicleHireRepository;
import com.tourlk.repo.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Availability is enforced with pessimistic locking for the same reason
 * as Accommodation's room availability check: two tourists hiring the
 * same vehicle for overlapping dates is a real, frequent race, and the
 * right behaviour is for the second request to wait and then get a
 * correct answer, not to fail on an optimistic-lock conflict and force a
 * blind retry. See {@code getLockedVehicle} below for the locking
 * mechanism. Unlike a Room, a Vehicle is a single unit, so the
 * availability check is existence (any overlap at all blocks), not a
 * capacity sum.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleHireServiceImpl implements VehicleHireService {

    private static final Set<VehicleHireStatus> TERMINAL_STATUSES =
            EnumSet.of(VehicleHireStatus.CANCELLED, VehicleHireStatus.COMPLETED);

    /** A hire holds its vehicle for these statuses; an unpaid PENDING hire blocks the dates too. */
    static final Set<VehicleHireStatus> HOLDING_STATUSES =
            EnumSet.of(VehicleHireStatus.PENDING, VehicleHireStatus.CONFIRMED);

    private final VehicleHireRepository vehicleHireRepository;
    private final VehicleRepository vehicleRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public VehicleHireResponseDto createHire(VehicleHireRequestDto request, User currentUser) {
        return doCreate(request.getVehicleId(), request.getStartDate(), request.getEndDate(),
                request.getPickupLocation(), request.getNotes(), currentUser, null);
    }

    @Override
    @Transactional
    public VehicleHireResponseDto createLinkedHire(Booking booking, Long vehicleId, LocalDate startDate,
                                                   LocalDate endDate, String pickupLocation) {
        return doCreate(vehicleId, startDate, endDate, pickupLocation, null, booking.getTourist(), booking);
    }

    private VehicleHireResponseDto doCreate(Long vehicleId, LocalDate startDate, LocalDate endDate,
                                            String pickupLocation, String notes, User currentUser, Booking booking) {
        assertDateRange(startDate, endDate);

        Vehicle vehicle = getLockedVehicle(vehicleId);

        if (vehicle.getStatus() != VehicleStatus.AVAILABLE && vehicle.getStatus() != VehicleStatus.BOOKED) {
            throw new BadRequestException("This vehicle is not currently available for hire");
        }

        assertAvailability(vehicle, startDate, endDate, 0L);

        BigDecimal totalPrice = vehicle.getPricePerDay().multiply(BigDecimal.valueOf(daysInclusive(
                startDate, endDate)));

        VehicleHire hire = VehicleHire.builder()
                .tourist(currentUser)
                .vehicle(vehicle)
                .startDate(startDate)
                .endDate(endDate)
                .pickupLocation(pickupLocation)
                .notes(notes)
                .totalPrice(totalPrice)
                .booking(booking)
                .status(VehicleHireStatus.PENDING)
                .build();

        VehicleHire saved = vehicleHireRepository.save(hire);
        notifyUser(vehicle.getDriver(), currentUser, NotificationType.VEHICLE_HIRE_REQUESTED, "New hire request",
                currentUser.getName() + " requested " + describe(vehicle) + " from " + saved.getStartDate()
                        + " to " + saved.getEndDate() + ".", "/vehicles/owner/hires");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public VehicleHireResponseDto confirmHire(Long id, User currentUser) {
        VehicleHire hire = getEntity(id);
        assertNotPartOfActiveBooking(hire, "confirmed");
        Vehicle vehicle = getLockedVehicle(hire.getVehicle().getId());
        assertOwnerOrAdmin(vehicle, currentUser);
        return doConfirm(hire, vehicle, currentUser);
    }

    @Override
    @Transactional
    public VehicleHireResponseDto confirmHireAfterPayment(Long id) {
        VehicleHire hire = getEntity(id);
        Vehicle vehicle = getLockedVehicle(hire.getVehicle().getId());
        return doConfirm(hire, vehicle, null);
    }

    /** {@code actor} is the owner/admin who confirmed, or null when a successful payment confirmed it. */
    private VehicleHireResponseDto doConfirm(VehicleHire hire, Vehicle vehicle, User actor) {
        assertStatus(hire, VehicleHireStatus.PENDING, "confirmed");
        assertAvailability(vehicle, hire.getStartDate(), hire.getEndDate(), hire.getId());

        hire.setStatus(VehicleHireStatus.CONFIRMED);
        VehicleHire saved = vehicleHireRepository.save(hire);
        notifyUser(saved.getTourist(), actor, NotificationType.VEHICLE_HIRE_CONFIRMED, "Hire confirmed",
                "Your hire of " + describe(vehicle) + " from " + saved.getStartDate() + " to " + saved.getEndDate()
                        + " is confirmed.", "/hires/mine");
        notifyUser(vehicle.getDriver(), actor, NotificationType.VEHICLE_HIRE_CONFIRMED, "Hire confirmed",
                describe(vehicle) + " is booked from " + saved.getStartDate() + " to " + saved.getEndDate() + ".",
                "/vehicles/owner/hires");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public VehicleHireResponseDto cancelHire(Long id, User currentUser) {
        VehicleHire hire = getEntity(id);
        assertTouristOrOwnerOrAdmin(hire, currentUser);
        assertNotPartOfActiveBooking(hire, "cancelled");

        if (TERMINAL_STATUSES.contains(hire.getStatus())) {
            throw new InvalidStatusTransitionException("This hire is already " + hire.getStatus());
        }

        hire.setStatus(VehicleHireStatus.CANCELLED);
        VehicleHire saved = vehicleHireRepository.save(hire);
        boolean byTourist = saved.getTourist().getId().equals(currentUser.getId());
        notifyUser(byTourist ? saved.getVehicle().getDriver() : saved.getTourist(), currentUser,
                NotificationType.VEHICLE_HIRE_CANCELLED, "Hire cancelled",
                "The hire of " + describe(saved.getVehicle()) + " from " + saved.getStartDate() + " to "
                        + saved.getEndDate() + " was cancelled.",
                byTourist ? "/vehicles/owner/hires" : "/hires/mine");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public VehicleHireResponseDto completeHire(Long id, User currentUser) {
        VehicleHire hire = getEntity(id);
        assertOwnerOrAdmin(hire.getVehicle(), currentUser);
        assertStatus(hire, VehicleHireStatus.CONFIRMED, "completed");

        hire.setStatus(VehicleHireStatus.COMPLETED);
        VehicleHire saved = vehicleHireRepository.save(hire);
        notifyUser(saved.getTourist(), currentUser, NotificationType.VEHICLE_HIRE_COMPLETED, "Hire completed",
                "Your hire of " + describe(saved.getVehicle()) + " is complete. How was it? Leave a review.",
                "/hires/mine");
        return toResponse(saved);
    }

    @Override
    public VehicleHireResponseDto getHireById(Long id, User currentUser) {
        VehicleHire hire = getEntity(id);
        assertTouristOrOwnerOrAdmin(hire, currentUser);
        return toResponse(hire);
    }

    @Override
    public List<VehicleHireResponseDto> getHiresByTourist(Long touristId) {
        return vehicleHireRepository.findByTouristId(touristId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<VehicleHireResponseDto> getHiresByVehicle(Long vehicleId, User currentUser) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + vehicleId));
        assertOwnerOrAdmin(vehicle, currentUser);

        return vehicleHireRepository.findByVehicleId(vehicleId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Locks the vehicle row for the rest of this transaction, serializing
     * every create/confirm availability check for that vehicle into one
     * at a time. See {@code VehicleRepository#findByIdForUpdate} for why
     * the vehicle row is locked instead of the overlap-check query.
     */
    private Vehicle getLockedVehicle(Long vehicleId) {
        return vehicleRepository.findByIdForUpdate(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + vehicleId));
    }

    /** {@code excludeHireId} is the hire being confirmed (so it doesn't clash with itself), or 0 for a new one. */
    private void assertAvailability(Vehicle vehicle, LocalDate startDate, LocalDate endDate, Long excludeHireId) {
        boolean overlapping = vehicleHireRepository.existsOverlapping(
                vehicle.getId(), HOLDING_STATUSES, startDate, endDate, excludeHireId);

        if (overlapping) {
            throw new VehicleUnavailableException(
                    "This vehicle is already booked from " + startDate + " to " + endDate + ".");
        }
    }

    @Override
    public List<BookedDateRangeDto> getBookedDates(Long vehicleId) {
        if (!vehicleRepository.existsById(vehicleId)) {
            throw new ResourceNotFoundException("Vehicle not found with id: " + vehicleId);
        }
        return vehicleHireRepository
                .findByVehicleIdAndStatusInAndEndDateGreaterThanEqualOrderByStartDateAsc(
                        vehicleId, HOLDING_STATUSES, LocalDate.now())
                .stream()
                .map(h -> new BookedDateRangeDto(h.getStartDate(), h.getEndDate()))
                .toList();
    }

    @Override
    @Transactional
    public void cancelLinkedToBooking(Long bookingId) {
        for (VehicleHire hire : vehicleHireRepository.findByBookingId(bookingId)) {
            if (TERMINAL_STATUSES.contains(hire.getStatus())) {
                continue;
            }
            hire.setStatus(VehicleHireStatus.CANCELLED);
            vehicleHireRepository.save(hire);
            notifyUser(hire.getVehicle().getDriver(), null, NotificationType.VEHICLE_HIRE_CANCELLED, "Hire cancelled",
                    "The hire of " + describe(hire.getVehicle()) + " from " + hire.getStartDate() + " to "
                            + hire.getEndDate() + " was cancelled because the package booking it belonged to was cancelled.",
                    "/vehicles/owner/hires");
        }
    }

    @Override
    @Transactional
    public void confirmLinkedAfterPayment(Long bookingId) {
        for (VehicleHire hire : vehicleHireRepository.findByBookingId(bookingId)) {
            if (hire.getStatus() != VehicleHireStatus.PENDING) {
                continue;
            }
            try {
                doConfirm(hire, getLockedVehicle(hire.getVehicle().getId()), null);
            } catch (RuntimeException ex) {
                log.error("Booking {} was paid but confirming its vehicle hire {} failed — needs manual review",
                        bookingId, hire.getId(), ex);
            }
        }
    }

    @Override
    @Transactional
    public void moveLinkedToDates(Long bookingId, LocalDate startDate, LocalDate endDate) {
        for (VehicleHire hire : vehicleHireRepository.findByBookingId(bookingId)) {
            if (TERMINAL_STATUSES.contains(hire.getStatus())) {
                continue;
            }
            Vehicle vehicle = getLockedVehicle(hire.getVehicle().getId());
            assertAvailability(vehicle, startDate, endDate, hire.getId());
            hire.setStartDate(startDate);
            hire.setEndDate(endDate);
            vehicleHireRepository.save(hire);
        }
    }

    @Override
    public List<VehicleHireResponseDto> getLinkedToBooking(Long bookingId) {
        return vehicleHireRepository.findByBookingId(bookingId).stream().map(this::toResponse).toList();
    }

    /** Add-ons are managed through their package booking while it is still active. */
    private void assertNotPartOfActiveBooking(VehicleHire hire, String action) {
        Booking booking = hire.getBooking();
        if (booking != null && !BookingStatus.TERMINAL.contains(booking.getStatus())) {
            throw new BadRequestException("This hire is part of the package booking #" + booking.getId()
                    + " (" + booking.getTourPackage().getTitle() + ") and can't be " + action
                    + " on its own — manage the package booking instead.");
        }
    }

    @Override
    @Transactional
    public boolean expireUnpaidHire(Long id) {
        VehicleHire hire = vehicleHireRepository.findById(id).orElse(null);
        if (hire == null || hire.getStatus() != VehicleHireStatus.PENDING) {
            return false;
        }
        hire.setStatus(VehicleHireStatus.CANCELLED);
        vehicleHireRepository.save(hire);
        notificationService.notify(hire.getTourist(), NotificationType.VEHICLE_HIRE_CANCELLED, "Hire expired",
                "Your hire of " + describe(hire.getVehicle()) + " from " + hire.getStartDate() + " to "
                        + hire.getEndDate() + " was cancelled because it was not paid in time.", "/hires/mine");
        return true;
    }

    private String describe(Vehicle vehicle) {
        return vehicle.getMake() + " " + vehicle.getModel();
    }

    /** In-app notice; skipped when the recipient is the person who just did it. */
    private void notifyUser(User recipient, User actor, NotificationType type, String title, String message,
                            String link) {
        if (actor == null || !recipient.getId().equals(actor.getId())) {
            notificationService.notify(recipient, type, title, message, link);
        }
    }

    private void assertDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new InvalidDateRangeException("End date must be on or after start date");
        }
    }

    private long daysInclusive(LocalDate startDate, LocalDate endDate) {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    private VehicleHire getEntity(Long id) {
        return vehicleHireRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hire not found with id: " + id));
    }

    private void assertOwnerOrAdmin(Vehicle vehicle, User currentUser) {
        boolean isOwner = vehicle.getDriver().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to manage this vehicle's hires");
        }
    }

    private void assertTouristOrOwnerOrAdmin(VehicleHire hire, User currentUser) {
        boolean isTourist = hire.getTourist().getId().equals(currentUser.getId());
        boolean isOwner = hire.getVehicle().getDriver().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isTourist && !isOwner && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to manage this hire");
        }
    }

    private void assertStatus(VehicleHire hire, VehicleHireStatus required, String action) {
        if (hire.getStatus() != required) {
            throw new InvalidStatusTransitionException(
                    "Only " + required + " hires can be " + action + ", but this hire is " + hire.getStatus());
        }
    }

    private VehicleHireResponseDto toResponse(VehicleHire hire) {
        Vehicle vehicle = hire.getVehicle();

        VehicleSummaryDto vehicleSummary = VehicleSummaryDto.builder()
                .id(vehicle.getId())
                .vehicleType(vehicle.getVehicleType())
                .make(vehicle.getMake())
                .model(vehicle.getModel())
                .registrationNumber(vehicle.getRegistrationNumber())
                .pricePerDay(vehicle.getPricePerDay())
                .build();

        return VehicleHireResponseDto.builder()
                .id(hire.getId())
                .vehicle(vehicleSummary)
                .touristId(hire.getTourist().getId())
                .touristName(hire.getTourist().getName())
                .startDate(hire.getStartDate())
                .endDate(hire.getEndDate())
                .pickupLocation(hire.getPickupLocation())
                .notes(hire.getNotes())
                .totalPrice(hire.getTotalPrice())
                .status(hire.getStatus())
                .bookingId(hire.getBooking() == null ? null : hire.getBooking().getId())
                .packageTitle(hire.getBooking() == null ? null : hire.getBooking().getTourPackage().getTitle())
                .createdAt(hire.getCreatedAt())
                .build();
    }

}
