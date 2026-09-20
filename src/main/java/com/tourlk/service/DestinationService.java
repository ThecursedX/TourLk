package com.tourlk.service;

import com.tourlk.dto.DestinationRequestDto;
import com.tourlk.dto.DestinationResponseDto;
import com.tourlk.entity.Destination;
import com.tourlk.enums.Province;

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

    DestinationResponseDto deactivateDestination(Long id);

    DestinationResponseDto reactivateDestination(Long id);

    /** Public — active destinations only, for dropdowns/browsing. */
    List<DestinationResponseDto> getAllActive();

    /** ADMIN — includes INACTIVE destinations. */
    List<DestinationResponseDto> getAll();

    DestinationResponseDto getById(Long id);

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
     * TourPackage/Accommodation: it must exist and be ACTIVE. Used by the
     * Tour Package and Accommodation modules during their migration to a
     * real destination relation.
     *
     * @throws com.tourlk.exception.ResourceNotFoundException    if no destination has this id
     * @throws com.tourlk.exception.DestinationInactiveException if the destination is INACTIVE
     */
    Destination requireSelectableDestination(Long id);

}
