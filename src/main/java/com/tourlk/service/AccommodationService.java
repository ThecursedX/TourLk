package com.tourlk.service;

import com.tourlk.dto.AccommodationRequestDto;
import com.tourlk.dto.AccommodationResponseDto;
import com.tourlk.dto.RoomRequestDto;
import com.tourlk.dto.RoomResponseDto;
import com.tourlk.entity.User;

import java.util.List;

public interface AccommodationService {

    AccommodationResponseDto createAccommodation(AccommodationRequestDto request, User currentUser);

    AccommodationResponseDto updateAccommodation(Long id, AccommodationRequestDto request, User currentUser);

    AccommodationResponseDto submitForApproval(Long id, User currentUser);

    AccommodationResponseDto approveAccommodation(Long id);

    AccommodationResponseDto rejectAccommodation(Long id);

    AccommodationResponseDto deactivateAccommodation(Long id, User currentUser);

    AccommodationResponseDto reactivateAccommodation(Long id, User currentUser);

    AccommodationResponseDto archiveAccommodation(Long id, User currentUser);

    AccommodationResponseDto getById(Long id);

    List<AccommodationResponseDto> getAllActive(Long locationId);

    List<AccommodationResponseDto> getPendingApproval();

    List<AccommodationResponseDto> getByOwner(Long ownerId);

    RoomResponseDto addRoom(Long accommodationId, RoomRequestDto request, User currentUser);

    RoomResponseDto updateRoom(Long roomId, RoomRequestDto request, User currentUser);

    void removeRoom(Long roomId, User currentUser);

}
