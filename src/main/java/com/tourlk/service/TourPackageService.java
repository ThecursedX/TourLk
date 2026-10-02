package com.tourlk.service;

import com.tourlk.dto.PackageSearchCriteria;
import com.tourlk.dto.TourPackageRequestDto;
import com.tourlk.dto.TourPackageResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.PackageStatus;

import java.util.List;

public interface TourPackageService {

    TourPackageResponseDto createPackage(TourPackageRequestDto request, User currentUser);

    /**
     * @param confirmChanges must be true to change price, duration or capacity of an ACTIVE package
     *                       with upcoming bookings; otherwise a ChangesRequireConfirmationException is thrown
     */
    TourPackageResponseDto updatePackage(Long id, TourPackageRequestDto request, User currentUser,
                                         boolean confirmChanges);

    TourPackageResponseDto submitForApproval(Long id, User currentUser);

    TourPackageResponseDto approvePackage(Long id);

    TourPackageResponseDto rejectPackage(Long id, String reason);

    TourPackageResponseDto deactivatePackage(Long id, User currentUser);

    TourPackageResponseDto reactivatePackage(Long id, User currentUser);

    TourPackageResponseDto archivePackage(Long id, User currentUser);

    /**
     * ACTIVE packages are visible to anyone (currentUser may be null); any
     * other status only to its owner or an admin — everyone else gets a
     * ResourceNotFoundException, so hidden packages don't reveal they exist.
     */
    TourPackageResponseDto getPackageById(Long id, User currentUser);

    /** Every package, optionally of one status, newest first. For admins. */
    List<TourPackageResponseDto> getAllPackagesForAdmin(PackageStatus status);

    /** ACTIVE packages matching every non-null criterion, in the requested order. */
    List<TourPackageResponseDto> browsePackages(PackageSearchCriteria criteria);

    List<TourPackageResponseDto> getPendingApprovalPackages();

    List<TourPackageResponseDto> getPackagesByCreator(Long userId);

}
