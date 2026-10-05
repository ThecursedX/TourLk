package com.tourlk.service;

import com.tourlk.dto.AccommodationRequestDto;
import com.tourlk.dto.AccommodationResponseDto;
import com.tourlk.dto.RoomRequestDto;
import com.tourlk.dto.RoomResponseDto;
import com.tourlk.entity.User;

import java.math.BigDecimal;
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

    /**
     * Public detail view. Listings that aren't live (draft, pending, inactive, archived) are
     * hidden (ResourceNotFoundException) from everyone except their owner and admins.
     * {@code currentUser} is null for anonymous callers.
     */
    AccommodationResponseDto getById(Long id, User currentUser);

    /**
     * Public browse of live listings (ACTIVE, FULLY_BOOKED, TEMPORARILY_UNAVAILABLE only), with optional
     * filters: name contains {@code q} (case-insensitive, literal), {@code minStars} 1-5, nightly price
     * range matched against any room type, and {@code sort} (name, price_asc, price_desc, stars_desc).
     * Invalid parameters throw BadRequestException.
     */
    List<AccommodationResponseDto> getAllActive(Long locationId, String q, Integer minStars,
                                                BigDecimal minPrice, BigDecimal maxPrice, String sort);

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
