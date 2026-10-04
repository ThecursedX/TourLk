package com.tourlk.service;

import com.tourlk.dto.AddOnAvailabilityDto;
import com.tourlk.dto.PackageAddOnItemDto;
import com.tourlk.dto.PackageAddOnResponseDto;
import com.tourlk.entity.User;

import java.time.LocalDate;
import java.util.List;

public interface PackageAddOnService {

    /** ACTIVE packages are public (currentUser may be null); others only for their owner or an admin. */
    List<PackageAddOnResponseDto> getAddOns(Long packageId, User currentUser);

    /**
     * Whether each add-on is free for the trip starting on {@code travelDate} (dates derived as when booking:
     * rooms {@code durationDays - 1} nights, vehicles every day of the trip). Same visibility rules as
     * {@link #getAddOns}.
     */
    List<AddOnAvailabilityDto> getAvailability(Long packageId, LocalDate travelDate, User currentUser);

    /** Replaces the package's add-ons with {@code items}. Package owner or ADMIN only. */
    List<PackageAddOnResponseDto> replaceAddOns(Long packageId, List<PackageAddOnItemDto> items, User currentUser);

}
