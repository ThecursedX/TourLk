package com.tourlk.service;

import com.tourlk.dto.TourPackageRequestDto;
import com.tourlk.dto.TourPackageResponseDto;
import com.tourlk.entity.User;

import java.math.BigDecimal;
import java.util.List;

public interface TourPackageService {

    TourPackageResponseDto createPackage(TourPackageRequestDto request, User currentUser);

    TourPackageResponseDto updatePackage(Long id, TourPackageRequestDto request, User currentUser);

    TourPackageResponseDto submitForApproval(Long id, User currentUser);

    TourPackageResponseDto approvePackage(Long id);

    TourPackageResponseDto rejectPackage(Long id);

    TourPackageResponseDto deactivatePackage(Long id, User currentUser);

    TourPackageResponseDto reactivatePackage(Long id, User currentUser);

    TourPackageResponseDto archivePackage(Long id, User currentUser);

    TourPackageResponseDto getPackageById(Long id);

    List<TourPackageResponseDto> getAllActivePackages();

    List<TourPackageResponseDto> getPendingApprovalPackages();

    List<TourPackageResponseDto> getPackagesByCreator(Long userId);

    List<TourPackageResponseDto> searchPackages(Long destinationId, BigDecimal minPrice, BigDecimal maxPrice);

}
