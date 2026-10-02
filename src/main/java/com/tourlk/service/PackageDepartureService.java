package com.tourlk.service;

import com.tourlk.dto.PackageDepartureRequestDto;
import com.tourlk.dto.PackageDepartureResponseDto;
import com.tourlk.entity.User;

import java.util.List;

public interface PackageDepartureService {

    PackageDepartureResponseDto addDeparture(Long packageId, PackageDepartureRequestDto request, User currentUser);

    void deleteDeparture(Long packageId, Long departureId, User currentUser);

    /** Departures dated after today, earliest first, with seats left. */
    List<PackageDepartureResponseDto> getUpcomingDepartures(Long packageId);

}
