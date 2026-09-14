package com.tourlk.service;

import com.tourlk.dto.VehicleHireRequestDto;
import com.tourlk.dto.VehicleHireResponseDto;
import com.tourlk.entity.User;

import java.util.List;

public interface VehicleHireService {

    VehicleHireResponseDto createHire(VehicleHireRequestDto request, User currentUser);

    VehicleHireResponseDto confirmHire(Long id, User currentUser);

    /**
     * Confirms a hire on behalf of the platform itself (e.g. after a
     * successful payment webhook), skipping the owner-or-admin check —
     * there is no authenticated actor in that flow. Mirrors
     * {@code RoomReservationService#confirmReservationAfterPayment}. Not
     * exposed via any controller; callable only from other services.
     */
    VehicleHireResponseDto confirmHireAfterPayment(Long id);

    VehicleHireResponseDto cancelHire(Long id, User currentUser);

    VehicleHireResponseDto completeHire(Long id, User currentUser);

    VehicleHireResponseDto getHireById(Long id, User currentUser);

    List<VehicleHireResponseDto> getHiresByTourist(Long touristId);

    List<VehicleHireResponseDto> getHiresByVehicle(Long vehicleId, User currentUser);

}
