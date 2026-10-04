package com.tourlk.service;

import com.tourlk.dto.VehicleRequestDto;
import com.tourlk.dto.VehicleResponseDto;
import com.tourlk.entity.User;
import com.tourlk.entity.Vehicle;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.Role;
import com.tourlk.enums.VehicleStatus;
import com.tourlk.enums.VehicleType;
import com.tourlk.exception.InvalidDateRangeException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.util.LicenceRules;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    /** Statuses visible in public browsing. */
    private static final Set<VehicleStatus> BROWSABLE = EnumSet.of(VehicleStatus.AVAILABLE, VehicleStatus.BOOKED);

    /** Statuses a vehicle can be taken out of service from. */
    private static final Set<VehicleStatus> IN_OPERATION =
            EnumSet.of(VehicleStatus.AVAILABLE, VehicleStatus.BOOKED, VehicleStatus.UNDER_MAINTENANCE);

    private final VehicleRepository vehicleRepository;
    private final NotificationService notificationService;

    @Override
    public VehicleResponseDto createVehicle(VehicleRequestDto request, User currentUser) {
        if (currentUser.getRole() == Role.DRIVER) {
            LicenceRules.assertVerifiedAndCurrent(currentUser,
                    "You must be a verified driver before you can register a vehicle", LocalDate.now());
        }

        assertMaintenanceDates(request);

        Vehicle vehicle = Vehicle.builder()
                .driver(currentUser)
                .vehicleType(request.getVehicleType())
                .make(request.getMake())
                .model(request.getModel())
                .registrationNumber(request.getRegistrationNumber())
                .seatingCapacity(request.getSeatingCapacity())
                .pricePerDay(request.getPricePerDay())
                .status(VehicleStatus.PENDING_VERIFICATION)
                .build();
        applyDetails(vehicle, request);

        Vehicle saved = vehicleRepository.save(vehicle);
        notifyAdminsOfSubmission(saved);
        return toResponse(saved);
    }

    @Override
    public VehicleResponseDto updateVehicle(Long id, VehicleRequestDto request, User currentUser) {
        Vehicle vehicle = getEntity(id);
        assertCanManage(vehicle, currentUser);
        assertMaintenanceDates(request);

        vehicle.setVehicleType(request.getVehicleType());
        vehicle.setMake(request.getMake());
        vehicle.setModel(request.getModel());
        vehicle.setRegistrationNumber(request.getRegistrationNumber());
        vehicle.setSeatingCapacity(request.getSeatingCapacity());
        vehicle.setPricePerDay(request.getPricePerDay());
        applyDetails(vehicle, request);

        return toResponse(vehicleRepository.save(vehicle));
    }

    @Override
    public VehicleResponseDto submitForVerification(Long id, User currentUser) {
        Vehicle vehicle = getEntity(id);
        assertCanManage(vehicle, currentUser);
        assertStatus(vehicle, VehicleStatus.DRAFT, "submitted for verification");

        vehicle.setStatus(VehicleStatus.PENDING_VERIFICATION);
        Vehicle saved = vehicleRepository.save(vehicle);
        notifyAdminsOfSubmission(saved);
        return toResponse(saved);
    }

    @Override
    public VehicleResponseDto approveVehicle(Long id) {
        Vehicle vehicle = getEntity(id);
        assertStatus(vehicle, VehicleStatus.PENDING_VERIFICATION, "approved");

        vehicle.setStatus(VehicleStatus.AVAILABLE);
        Vehicle saved = vehicleRepository.save(vehicle);
        notificationService.notify(saved.getDriver(), NotificationType.VEHICLE_APPROVED, "Vehicle approved",
                describe(saved) + " was approved and is now available for hire.", "/vehicles/mine");
        return toResponse(saved);
    }

    @Override
    public VehicleResponseDto rejectVehicle(Long id) {
        Vehicle vehicle = getEntity(id);
        assertStatus(vehicle, VehicleStatus.PENDING_VERIFICATION, "rejected");

        // Back to DRAFT so the driver can fix the listing and resubmit it.
        vehicle.setStatus(VehicleStatus.DRAFT);
        Vehicle saved = vehicleRepository.save(vehicle);
        notificationService.notify(saved.getDriver(), NotificationType.VEHICLE_REJECTED, "Vehicle not approved",
                describe(saved) + " was not approved. Update the listing and submit it again.", "/vehicles/mine");
        return toResponse(saved);
    }

    @Override
    public VehicleResponseDto deactivateVehicle(Long id, User currentUser) {
        Vehicle vehicle = getEntity(id);
        assertCanManage(vehicle, currentUser);
        assertInOperation(vehicle, "taken out of service");

        vehicle.setStatus(VehicleStatus.OUT_OF_SERVICE);
        Vehicle saved = vehicleRepository.save(vehicle);
        notifyOwnerOfStatusChange(saved, currentUser, "taken out of service");
        return toResponse(saved);
    }

    @Override
    public VehicleResponseDto startMaintenance(Long id, User currentUser) {
        Vehicle vehicle = getEntity(id);
        assertCanManage(vehicle, currentUser);
        if (vehicle.getStatus() != VehicleStatus.AVAILABLE && vehicle.getStatus() != VehicleStatus.BOOKED) {
            throw new InvalidStatusTransitionException(
                    "Only AVAILABLE or BOOKED vehicles can be put under maintenance, but this vehicle is "
                            + vehicle.getStatus());
        }

        vehicle.setStatus(VehicleStatus.UNDER_MAINTENANCE);
        Vehicle saved = vehicleRepository.save(vehicle);
        notifyOwnerOfStatusChange(saved, currentUser, "put under maintenance");
        return toResponse(saved);
    }

    @Override
    public VehicleResponseDto reactivateVehicle(Long id, User currentUser) {
        Vehicle vehicle = getEntity(id);
        assertCanManage(vehicle, currentUser);
        if (vehicle.getStatus() != VehicleStatus.OUT_OF_SERVICE
                && vehicle.getStatus() != VehicleStatus.UNDER_MAINTENANCE) {
            throw new InvalidStatusTransitionException(
                    "Only OUT_OF_SERVICE or UNDER_MAINTENANCE vehicles can be reactivated, but this vehicle is "
                            + vehicle.getStatus());
        }

        vehicle.setStatus(VehicleStatus.AVAILABLE);
        Vehicle saved = vehicleRepository.save(vehicle);
        notifyOwnerOfStatusChange(saved, currentUser, "made available again");
        return toResponse(saved);
    }

    @Override
    public VehicleResponseDto archiveVehicle(Long id, User currentUser) {
        Vehicle vehicle = getEntity(id);
        assertCanManage(vehicle, currentUser);

        if (vehicle.getStatus() == VehicleStatus.ARCHIVED) {
            throw new InvalidStatusTransitionException("This vehicle is already archived");
        }
        // Out-of-service vehicles keep their record (hire history, compliance
        // dates) and are never deleted/archived; reactivate them instead.
        if (vehicle.getStatus() == VehicleStatus.OUT_OF_SERVICE) {
            throw new InvalidStatusTransitionException(
                    "Out-of-service vehicles cannot be deleted. Reactivate the vehicle first if it is no longer needed");
        }

        vehicle.setStatus(VehicleStatus.ARCHIVED);
        return toResponse(vehicleRepository.save(vehicle));
    }

    @Override
    public VehicleResponseDto getById(Long id) {
        return toResponse(getEntity(id));
    }

    @Override
    public List<VehicleResponseDto> getAllActive(VehicleType vehicleType, Integer minSeatingCapacity) {
        return vehicleRepository.search(BROWSABLE, vehicleType, minSeatingCapacity).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<VehicleResponseDto> getPendingApproval() {
        return vehicleRepository.findByStatus(VehicleStatus.PENDING_VERIFICATION).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<VehicleResponseDto> getByDriver(Long driverId) {
        return vehicleRepository.findByDriverId(driverId).stream()
                .map(this::toResponse)
                .toList();
    }

    private Vehicle getEntity(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
    }

    private void assertCanManage(Vehicle vehicle, User currentUser) {
        boolean isOwner = vehicle.getDriver().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to manage this vehicle");
        }
    }

    private void assertStatus(Vehicle vehicle, VehicleStatus required, String action) {
        if (vehicle.getStatus() != required) {
            throw new InvalidStatusTransitionException(
                    "Only " + required + " vehicles can be " + action + ", but this vehicle is "
                            + vehicle.getStatus());
        }
    }

    private void notifyAdminsOfSubmission(Vehicle vehicle) {
        notificationService.notifyAdmins(NotificationType.VEHICLE_SUBMITTED, "Vehicle awaiting verification",
                describe(vehicle) + " was submitted for verification", "/admin/vehicles");
    }

    private String describe(Vehicle vehicle) {
        return vehicle.getMake() + " " + vehicle.getModel() + " (" + vehicle.getRegistrationNumber() + ")";
    }

    /** Tells the owner when someone else (an admin) changed their vehicle's status. */
    private void notifyOwnerOfStatusChange(Vehicle vehicle, User actor, String what) {
        if (!vehicle.getDriver().getId().equals(actor.getId())) {
            notificationService.notify(vehicle.getDriver(), NotificationType.VEHICLE_STATUS_CHANGED,
                    "Vehicle status changed", describe(vehicle) + " was " + what + " by an administrator.",
                    "/vehicles/mine");
        }
    }

    private void assertInOperation(Vehicle vehicle, String action) {
        if (!IN_OPERATION.contains(vehicle.getStatus())) {
            throw new InvalidStatusTransitionException(
                    "Only AVAILABLE, BOOKED or UNDER_MAINTENANCE vehicles can be " + action
                            + ", but this vehicle is " + vehicle.getStatus());
        }
    }

    private void assertMaintenanceDates(VehicleRequestDto request) {
        LocalDate last = request.getLastMaintenanceDate();
        LocalDate next = request.getNextMaintenanceDate();
        if (last != null && next != null && next.isBefore(last)) {
            throw new InvalidDateRangeException("Next maintenance date must be on or after the last maintenance date");
        }
    }

    /** Copies the optional detail fields (everything beyond the core listing) from the request. */
    private void applyDetails(Vehicle vehicle, VehicleRequestDto request) {
        vehicle.setAirConditioned(request.getAirConditioned());
        vehicle.setFacilities(copyOrEmpty(request.getFacilities()));
        vehicle.setImageUrls(copyOrEmpty(request.getImageUrls()));
        vehicle.setDriverName(blankToNull(request.getDriverName()));
        vehicle.setDriverPhone(blankToNull(request.getDriverPhone()));
        vehicle.setInsuranceExpiry(request.getInsuranceExpiry());
        vehicle.setLastMaintenanceDate(request.getLastMaintenanceDate());
        vehicle.setNextMaintenanceDate(request.getNextMaintenanceDate());
    }

    private List<String> copyOrEmpty(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private VehicleResponseDto toResponse(Vehicle vehicle) {
        return VehicleResponseDto.builder()
                .id(vehicle.getId())
                .vehicleType(vehicle.getVehicleType())
                .make(vehicle.getMake())
                .model(vehicle.getModel())
                .registrationNumber(vehicle.getRegistrationNumber())
                .seatingCapacity(vehicle.getSeatingCapacity())
                .pricePerDay(vehicle.getPricePerDay())
                .status(vehicle.getStatus())
                .driverId(vehicle.getDriver().getId())
                .driverName(vehicle.getDriverName() != null
                        ? vehicle.getDriverName() : vehicle.getDriver().getName())
                .driverPhone(vehicle.getDriverPhone() != null
                        ? vehicle.getDriverPhone() : vehicle.getDriver().getPhone())
                .airConditioned(Boolean.TRUE.equals(vehicle.getAirConditioned()))
                .facilities(vehicle.getFacilities() == null ? List.of() : List.copyOf(vehicle.getFacilities()))
                .imageUrls(vehicle.getImageUrls() == null ? List.of() : List.copyOf(vehicle.getImageUrls()))
                .insuranceExpiry(vehicle.getInsuranceExpiry())
                .lastMaintenanceDate(vehicle.getLastMaintenanceDate())
                .nextMaintenanceDate(vehicle.getNextMaintenanceDate())
                .build();
    }

}
