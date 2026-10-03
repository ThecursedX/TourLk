package com.tourlk.service;

import com.tourlk.dto.AddOnAvailabilityDto;
import com.tourlk.dto.PackageAddOnItemDto;
import com.tourlk.dto.PackageAddOnResponseDto;
import com.tourlk.entity.Accommodation;
import com.tourlk.entity.PackageAddOn;
import com.tourlk.entity.Room;
import com.tourlk.entity.TourPackage;
import com.tourlk.entity.User;
import com.tourlk.entity.Vehicle;
import com.tourlk.enums.AccommodationStatus;
import com.tourlk.enums.PackageStatus;
import com.tourlk.enums.Role;
import com.tourlk.enums.VehicleStatus;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.PackageAddOnRepository;
import com.tourlk.repo.RoomRepository;
import com.tourlk.repo.RoomReservationRepository;
import com.tourlk.repo.TourPackageRepository;
import com.tourlk.repo.VehicleHireRepository;
import com.tourlk.repo.VehicleRepository;
import com.tourlk.util.AddOnDates;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PackageAddOnServiceImpl implements PackageAddOnService {

    private final PackageAddOnRepository addOnRepository;
    private final TourPackageRepository tourPackageRepository;
    private final RoomRepository roomRepository;
    private final VehicleRepository vehicleRepository;
    private final RoomReservationRepository roomReservationRepository;
    private final VehicleHireRepository vehicleHireRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PackageAddOnResponseDto> getAddOns(Long packageId, User currentUser) {
        TourPackage tourPackage = getPackage(packageId);
        if (tourPackage.getStatus() != PackageStatus.ACTIVE && !isOwnerOrAdmin(tourPackage, currentUser)) {
            // Hidden packages don't reveal they exist.
            throw new ResourceNotFoundException("Tour package not found with id: " + packageId);
        }
        return addOnRepository.findByTourPackageId(packageId).stream()
                .map(PackageAddOnMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddOnAvailabilityDto> getAvailability(Long packageId, LocalDate travelDate, User currentUser) {
        TourPackage tourPackage = getPackage(packageId);
        if (tourPackage.getStatus() != PackageStatus.ACTIVE && !isOwnerOrAdmin(tourPackage, currentUser)) {
            throw new ResourceNotFoundException("Tour package not found with id: " + packageId);
        }
        int days = tourPackage.getDurationDays();
        LocalDate checkIn = AddOnDates.roomCheckIn(travelDate);
        LocalDate checkOut = AddOnDates.roomCheckOut(travelDate, days);
        LocalDate vehicleStart = AddOnDates.vehicleStart(travelDate);
        LocalDate vehicleEnd = AddOnDates.vehicleEnd(travelDate, days);

        List<AddOnAvailabilityDto> result = new ArrayList<>();
        for (PackageAddOn addOn : addOnRepository.findByTourPackageId(packageId)) {
            if (addOn.getRoom() != null) {
                Room room = addOn.getRoom();
                int held = roomReservationRepository.sumReservedRoomsOverlapping(
                        room.getId(), RoomReservationServiceImpl.HOLDING_STATUSES, checkIn, checkOut, 0L);
                int left = Math.max(0, room.getTotalRooms() - held);
                boolean open = room.getAccommodation().getStatus() == AccommodationStatus.ACTIVE
                        || room.getAccommodation().getStatus() == AccommodationStatus.FULLY_BOOKED;
                result.add(AddOnAvailabilityDto.builder()
                        .roomId(room.getId()).available(open && left > 0).roomsLeft(left).build());
            } else if (addOn.getVehicle() != null) {
                Vehicle vehicle = addOn.getVehicle();
                boolean hireable = vehicle.getStatus() == VehicleStatus.AVAILABLE
                        || vehicle.getStatus() == VehicleStatus.BOOKED;
                boolean taken = vehicleHireRepository.existsOverlapping(vehicle.getId(),
                        VehicleHireServiceImpl.HOLDING_STATUSES, vehicleStart, vehicleEnd, 0L);
                result.add(AddOnAvailabilityDto.builder()
                        .vehicleId(vehicle.getId()).available(hireable && !taken).build());
            }
        }
        return result;
    }

    @Override
    @Transactional
    public List<PackageAddOnResponseDto> replaceAddOns(Long packageId, List<PackageAddOnItemDto> items,
                                                        User currentUser) {
        TourPackage tourPackage = getPackage(packageId);
        if (!isOwnerOrAdmin(tourPackage, currentUser)) {
            throw new AccessDeniedException("Only the package owner or an admin can change its add-ons");
        }

        List<PackageAddOn> fresh = new ArrayList<>();
        Set<Long> roomIds = new HashSet<>();
        Set<Long> vehicleIds = new HashSet<>();
        for (PackageAddOnItemDto item : items) {
            if ((item.getRoomId() == null) == (item.getVehicleId() == null)) {
                throw new BadRequestException("Each add-on must be exactly one of a room or a vehicle");
            }
            String note = item.getNote() == null || item.getNote().isBlank() ? null : item.getNote().trim();

            if (item.getRoomId() != null) {
                if (!roomIds.add(item.getRoomId())) {
                    throw new BadRequestException("The same room can only be added once");
                }
                fresh.add(PackageAddOn.builder()
                        .tourPackage(tourPackage).room(requireAttachableRoom(tourPackage, item.getRoomId()))
                        .note(note).build());
            } else {
                if (!vehicleIds.add(item.getVehicleId())) {
                    throw new BadRequestException("The same vehicle can only be added once");
                }
                fresh.add(PackageAddOn.builder()
                        .tourPackage(tourPackage).vehicle(requireAttachableVehicle(item.getVehicleId()))
                        .note(note).build());
            }
        }

        addOnRepository.deleteAll(addOnRepository.findByTourPackageId(packageId));
        addOnRepository.flush();
        return addOnRepository.saveAll(fresh).stream().map(PackageAddOnMapper::toResponse).toList();
    }

    private Room requireAttachableRoom(TourPackage tourPackage, Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + roomId));
        Accommodation accommodation = room.getAccommodation();
        if (!accommodation.getLocation().getId().equals(tourPackage.getDestination().getId())) {
            throw new BadRequestException(accommodation.getName() + " is not in "
                    + tourPackage.getDestination().getName() + ", the destination of this package");
        }
        if (accommodation.getStatus() != AccommodationStatus.ACTIVE) {
            throw new BadRequestException(accommodation.getName() + " is not an active accommodation");
        }
        return room;
    }

    private Vehicle requireAttachableVehicle(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + vehicleId));
        if (vehicle.getStatus() != VehicleStatus.AVAILABLE) {
            throw new BadRequestException(vehicle.getMake() + " " + vehicle.getModel()
                    + " is not an approved, available vehicle");
        }
        return vehicle;
    }

    private TourPackage getPackage(Long id) {
        return tourPackageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tour package not found with id: " + id));
    }

    private boolean isOwnerOrAdmin(TourPackage tourPackage, User user) {
        return user != null && (user.getRole() == Role.ADMIN
                || tourPackage.getCreatedBy().getId().equals(user.getId()));
    }

}
