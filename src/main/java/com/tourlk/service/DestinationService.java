package com.tourlk.service;

import com.tourlk.dto.DestinationRequestDto;
import com.tourlk.dto.DestinationResponseDto;
import com.tourlk.entity.Destination;
import com.tourlk.enums.Province;

import java.time.LocalDate;
import java.util.List;

/**
 * Destination catalogue management. Write operations are ADMIN-only
 * (enforced at the controller via {@code @PreAuthorize}); read
 * operations are split into a public "active only" view and an
 * ADMIN "everything" view.
 */
public interface DestinationService {

    DestinationResponseDto createDestination(DestinationRequestDto request);

    DestinationResponseDto updateDestination(Long id, DestinationRequestDto request);

    /** DRAFT -> PENDING_REVIEW. */
    DestinationResponseDto submitForReview(Long id);

    /** DRAFT / PENDING_REVIEW -> PUBLISHED. */
    DestinationResponseDto publishDestination(Long id);

    /** PUBLISHED -> TEMPORARILY_CLOSED with a reason and an optional last day of closure. */
    DestinationResponseDto closeTemporarily(Long id, String reason, LocalDate until);

    /** TEMPORARILY_CLOSED -> PUBLISHED, clearing the closure details. */
    DestinationResponseDto reopenDestination(Long id);

    /** Soft delete: hidden publicly, kept for admins. */
    DestinationResponseDto archiveDestination(Long id);

    /** Reopens every TEMPORARILY_CLOSED destination whose closure ended before {@code today}. */
    int reopenExpiredClosures(LocalDate today);

    DestinationResponseDto deactivateDestination(Long id);

    DestinationResponseDto reactivateDestination(Long id);

    /** Public — PUBLISHED and TEMPORARILY_CLOSED destinations, for browsing. */
    List<DestinationResponseDto> getAllActive();

    /** ADMIN — every destination, whatever its status (incl. DRAFT, INACTIVE, ARCHIVED). */
    List<DestinationResponseDto> getAll();

    /**
     * Admins see every destination; everyone else gets a 404 for DRAFT,
     * PENDING_REVIEW and ARCHIVED ones (INACTIVE stays viewable, since existing
     * packages and stays may still link to it).
     */
    DestinationResponseDto getById(Long id, boolean isAdmin);

    /**
     * Public destinations within {@code radiusKm} of a point, nearest first,
     * with {@code distanceKm} filled in (haversine). {@code nearby} is
     * {@code "lat,lng"}; a null radius defaults to 50 km.
     *
     * @throws com.tourlk.exception.BadRequestException on a malformed point or out-of-range value
     */
    List<DestinationResponseDto> searchNearby(String nearby, Double radiusKm);

    List<DestinationResponseDto> searchByName(String query);

    /** Public-facing: active destinations in one province. */
    List<DestinationResponseDto> getByProvince(Province province);

    /**
     * Category suggestions for the admin form: a built-in starter list
     * merged with every category already in use, sorted and de-duplicated
     * case-insensitively.
     */
    List<String> getCategorySuggestions();

    /**
     * Resolves a destination that is valid to attach to a NEW or EDITED
     * TourPackage/Accommodation: it must exist and be PUBLISHED or TEMPORARILY_CLOSED. Used by the
     * Tour Package and Accommodation modules during their migration to a
     * real destination relation.
     *
     * @throws com.tourlk.exception.ResourceNotFoundException    if no destination has this id
     * @throws com.tourlk.exception.DestinationInactiveException if the destination is not selectable
     */
    Destination requireSelectableDestination(Long id);

}
