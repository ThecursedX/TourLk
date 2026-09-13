package com.tourlk.service;

import com.tourlk.dto.VehicleRequestDto;
import com.tourlk.dto.VehicleResponseDto;
import com.tourlk.entity.User;
import com.tourlk.entity.Vehicle;
import com.tourlk.enums.Role;
import com.tourlk.enums.VehicleStatus;
import com.tourlk.enums.VehicleType;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;

    @Override
    public VehicleResponseDto createVehicle(VehicleRequestDto request, User currentUser) {
        Vehicle vehicle = Vehicle.builder()
                .driver(currentUser)
                .vehicleType(request.getVehicleType())
                .make(request.getMake())
                .model(request.getModel())
                .registrationNumber(request.getRegistrationNumber())
                .seatingCapacity(request.getSeatingCapacity())
                .pricePerDay(request.getPricePerDay())
                .status(VehicleStatus.PENDING_APPROVAL)
                .build();

        return toResponse(vehicleRepository.save(vehicle));
    }

    @Override
    public VehicleResponseDto updateVehicle(Long id, VehicleRequestDto request, User currentUser) {
        Vehicle vehicle = getEntity(id);
        assertCanManage(vehicle, currentUser);

        vehicle.setVehicleType(request.getVehicleType());
        vehicle.setMake(request.getMake());
        vehicle.setModel(request.getModel());
        vehicle.setRegistrationNumber(request.getRegistrationNumber());
        vehicle.setSeatingCapacity(request.getSeatingCapacity());
        vehicle.setPricePerDay(request.getPricePerDay());

        return toResponse(vehicleRepository.save(vehicle));
    }

    @Override
    public VehicleResponseDto approveVehicle(Long id) {
        Vehicle vehicle = getEntity(id);
        assertStatus(vehicle, VehicleStatus.PENDING_APPROVAL, "approved");

        vehicle.setStatus(VehicleStatus.ACTIVE);
        return toResponse(vehicleRepository.save(vehicle));
    }

    @Override
    public VehicleResponseDto rejectVehicle(Long id) {
        Vehicle vehicle = getEntity(id);
        assertStatus(vehicle, VehicleStatus.PENDING_APPROVAL, "rejected");

        // No DRAFT status exists to revert a vehicle to (unlike TourPackage/
        // Accommodation) — a rejected listing is archived; the driver
        // registers a new one rather than editing and resubmitting this one.
        vehicle.setStatus(VehicleStatus.ARCHIVED);
        return toResponse(vehicleRepository.save(vehicle));
    }

    @Override
    public VehicleResponseDto deactivateVehicle(Long id, User currentUser) {
        Vehicle vehicle = getEntity(id);
        assertCanManage(vehicle, currentUser);
        assertStatus(vehicle, VehicleStatus.ACTIVE, "deactivated");

        vehicle.setStatus(VehicleStatus.INACTIVE);
        return toResponse(vehicleRepository.save(vehicle));
    }

    @Override
    public VehicleResponseDto reactivateVehicle(Long id, User currentUser) {
        Vehicle vehicle = getEntity(id);
        assertCanManage(vehicle, currentUser);
        assertStatus(vehicle, VehicleStatus.INACTIVE, "reactivated");

        vehicle.setStatus(VehicleStatus.ACTIVE);
        return toResponse(vehicleRepository.save(vehicle));
    }

    @Override
    public VehicleResponseDto archiveVehicle(Long id, User currentUser) {
        Vehicle vehicle = getEntity(id);
        assertCanManage(vehicle, currentUser);

        if (vehicle.getStatus() == VehicleStatus.ARCHIVED) {
            throw new InvalidStatusTransitionException("This vehicle is already archived");
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
        return vehicleRepository.search(VehicleStatus.ACTIVE, vehicleType, minSeatingCapacity).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<VehicleResponseDto> getPendingApproval() {
        return vehicleRepository.findByStatus(VehicleStatus.PENDING_APPROVAL).stream()
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
                .driverName(vehicle.getDriver().getName())
                .build();
    }

}
