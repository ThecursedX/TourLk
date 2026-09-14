package com.tourlk.service;

import com.tourlk.dto.VehicleRequestDto;
import com.tourlk.dto.VehicleResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.VehicleType;

import java.util.List;

public interface VehicleService {

    VehicleResponseDto createVehicle(VehicleRequestDto request, User currentUser);

    VehicleResponseDto updateVehicle(Long id, VehicleRequestDto request, User currentUser);

    VehicleResponseDto approveVehicle(Long id);

    VehicleResponseDto rejectVehicle(Long id);

    VehicleResponseDto deactivateVehicle(Long id, User currentUser);

    VehicleResponseDto reactivateVehicle(Long id, User currentUser);

    VehicleResponseDto archiveVehicle(Long id, User currentUser);

    VehicleResponseDto getById(Long id);

    List<VehicleResponseDto> getAllActive(VehicleType vehicleType, Integer minSeatingCapacity);

    List<VehicleResponseDto> getPendingApproval();

    List<VehicleResponseDto> getByDriver(Long driverId);

}
