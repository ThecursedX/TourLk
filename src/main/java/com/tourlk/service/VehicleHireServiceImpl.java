package com.tourlk.service;

import com.tourlk.dto.VehicleHireRequestDto;
import com.tourlk.dto.VehicleHireResponseDto;
import com.tourlk.dto.VehicleSummaryDto;
import com.tourlk.entity.User;
import com.tourlk.entity.Vehicle;
import com.tourlk.entity.VehicleHire;
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
@Service
@RequiredArgsConstructor
public class VehicleHireServiceImpl implements VehicleHireService {

    private static final Set<VehicleHireStatus> TERMINAL_STATUSES =
            EnumSet.of(VehicleHireStatus.CANCELLED, VehicleHireStatus.COMPLETED);

    private final VehicleHireRepository vehicleHireRepository;
    private final VehicleRepository vehicleRepository;

    @Override
    @Transactional
    public VehicleHireResponseDto createHire(VehicleHireRequestDto request, User currentUser) {
        assertDateRange(request.getStartDate(), request.getEndDate());

        Vehicle vehicle = getLockedVehicle(request.getVehicleId());

        if (vehicle.getStatus() != VehicleStatus.ACTIVE) {
            throw new BadRequestException("This vehicle is not currently available for hire");
        }

        assertAvailability(vehicle, request.getStartDate(), request.getEndDate());

        BigDecimal totalPrice = vehicle.getPricePerDay().multiply(BigDecimal.valueOf(daysInclusive(
                request.getStartDate(), request.getEndDate())));

        VehicleHire hire = VehicleHire.builder()
                .tourist(currentUser)
                .vehicle(vehicle)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .pickupLocation(request.getPickupLocation())
                .notes(request.getNotes())
                .totalPrice(totalPrice)
                .status(VehicleHireStatus.PENDING)
                .build();

        return toResponse(vehicleHireRepository.save(hire));
    }

    @Override
    @Transactional
    public VehicleHireResponseDto confirmHire(Long id, User currentUser) {
        VehicleHire hire = getEntity(id);
        Vehicle vehicle = getLockedVehicle(hire.getVehicle().getId());
        assertOwnerOrAdmin(vehicle, currentUser);
        return doConfirm(hire, vehicle);
    }

    @Override
    @Transactional
    public VehicleHireResponseDto confirmHireAfterPayment(Long id) {
        VehicleHire hire = getEntity(id);
        Vehicle vehicle = getLockedVehicle(hire.getVehicle().getId());
        return doConfirm(hire, vehicle);
    }

    private VehicleHireResponseDto doConfirm(VehicleHire hire, Vehicle vehicle) {
        assertStatus(hire, VehicleHireStatus.PENDING, "confirmed");
        assertAvailability(vehicle, hire.getStartDate(), hire.getEndDate());

        hire.setStatus(VehicleHireStatus.CONFIRMED);
        return toResponse(vehicleHireRepository.save(hire));
    }

    @Override
    @Transactional
    public VehicleHireResponseDto cancelHire(Long id, User currentUser) {
        VehicleHire hire = getEntity(id);
        assertTouristOrOwnerOrAdmin(hire, currentUser);

        if (TERMINAL_STATUSES.contains(hire.getStatus())) {
            throw new InvalidStatusTransitionException("This hire is already " + hire.getStatus());
        }

        hire.setStatus(VehicleHireStatus.CANCELLED);
        return toResponse(vehicleHireRepository.save(hire));
    }

    @Override
    @Transactional
    public VehicleHireResponseDto completeHire(Long id, User currentUser) {
        VehicleHire hire = getEntity(id);
        assertOwnerOrAdmin(hire.getVehicle(), currentUser);
        assertStatus(hire, VehicleHireStatus.CONFIRMED, "completed");

        hire.setStatus(VehicleHireStatus.COMPLETED);
        return toResponse(vehicleHireRepository.save(hire));
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

    private void assertAvailability(Vehicle vehicle, LocalDate startDate, LocalDate endDate) {
        boolean overlapping = vehicleHireRepository.existsOverlapping(
                vehicle.getId(), VehicleHireStatus.CONFIRMED, startDate, endDate);

        if (overlapping) {
            throw new VehicleUnavailableException(
                    "This vehicle is already booked for part of " + startDate + " to " + endDate);
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
                .createdAt(hire.getCreatedAt())
                .build();
    }

}
