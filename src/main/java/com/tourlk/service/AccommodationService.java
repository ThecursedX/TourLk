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

    /** Hotel partner pauses bookings: stays visible, but not bookable, until resumed. */
    AccommodationResponseDto markTemporarilyUnavailable(Long id, User currentUser);

    /** Ends a TEMPORARILY_UNAVAILABLE pause; the listing reopens (or shows as fully booked). */
    AccommodationResponseDto resumeAvailability(Long id, User currentUser);

    AccommodationResponseDto archiveAccommodation(Long id, User currentUser);

    AccommodationResponseDto getById(Long id);

    List<AccommodationResponseDto> getAllActive(Long locationId);

    List<AccommodationResponseDto> getPendingApproval();

    List<AccommodationResponseDto> getByOwner(Long ownerId);

    RoomResponseDto addRoom(Long accommodationId, RoomRequestDto request, User currentUser);

    RoomResponseDto updateRoom(Long roomId, RoomRequestDto request, User currentUser);

    void removeRoom(Long roomId, User currentUser);

    /**
     * Re-derives ACTIVE vs FULLY_BOOKED for one accommodation from today's
     * confirmed reservations. Joins the caller's transaction (called from
     * the reservation service after every confirm/cancel/complete).
     */
    void refreshAvailabilityStatus(Long accommodationId);

    /** Same, for every ACTIVE / FULLY_BOOKED accommodation; run daily because "today" moves. */
    void refreshAllAvailabilityStatuses();

}
