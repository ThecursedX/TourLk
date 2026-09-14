package com.tourlk.service;

import com.tourlk.dto.DestinationResponseDto;
import com.tourlk.dto.TourPackageRequestDto;
import com.tourlk.dto.TourPackageResponseDto;
import com.tourlk.entity.Destination;
import com.tourlk.entity.TourPackage;
import com.tourlk.entity.User;
import com.tourlk.enums.PackageStatus;
import com.tourlk.enums.Role;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.TourPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TourPackageServiceImpl implements TourPackageService {

    private final TourPackageRepository tourPackageRepository;
    private final DestinationService destinationService;

    @Override
    public TourPackageResponseDto createPackage(TourPackageRequestDto request, User currentUser) {
        Destination destination = destinationService.requireSelectableDestination(request.getDestinationId());

        TourPackage tourPackage = TourPackage.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .destination(destination)
                .durationDays(request.getDurationDays())
                .price(request.getPrice())
                .maxCapacity(request.getMaxCapacity())
                .status(PackageStatus.DRAFT)
                .createdBy(currentUser)
                .build();

        return toResponse(tourPackageRepository.save(tourPackage));
    }

    @Override
    public TourPackageResponseDto updatePackage(Long id, TourPackageRequestDto request, User currentUser) {
        TourPackage tourPackage = getEntity(id);
        assertCanManage(tourPackage, currentUser);

        if (tourPackage.getStatus() != PackageStatus.DRAFT && tourPackage.getStatus() != PackageStatus.ACTIVE) {
            throw new InvalidStatusTransitionException(
                    "A package can only be edited while it is DRAFT or ACTIVE, not " + tourPackage.getStatus());
        }

        tourPackage.setTitle(request.getTitle());
        tourPackage.setDescription(request.getDescription());
        tourPackage.setDestination(destinationService.requireSelectableDestination(request.getDestinationId()));
        tourPackage.setDurationDays(request.getDurationDays());
        tourPackage.setPrice(request.getPrice());
        tourPackage.setMaxCapacity(request.getMaxCapacity());

        return toResponse(tourPackageRepository.save(tourPackage));
    }

    @Override
    public TourPackageResponseDto submitForApproval(Long id, User currentUser) {
        TourPackage tourPackage = getEntity(id);
        assertCanManage(tourPackage, currentUser);
        assertStatus(tourPackage, PackageStatus.DRAFT, "submitted for approval");

        tourPackage.setStatus(PackageStatus.PENDING_APPROVAL);
        return toResponse(tourPackageRepository.save(tourPackage));
    }

    @Override
    public TourPackageResponseDto approvePackage(Long id) {
        TourPackage tourPackage = getEntity(id);
        assertStatus(tourPackage, PackageStatus.PENDING_APPROVAL, "approved");

        tourPackage.setStatus(PackageStatus.ACTIVE);
        return toResponse(tourPackageRepository.save(tourPackage));
    }

    @Override
    public TourPackageResponseDto rejectPackage(Long id) {
        TourPackage tourPackage = getEntity(id);
        assertStatus(tourPackage, PackageStatus.PENDING_APPROVAL, "rejected");

        tourPackage.setStatus(PackageStatus.DRAFT);
        return toResponse(tourPackageRepository.save(tourPackage));
    }

    @Override
    public TourPackageResponseDto deactivatePackage(Long id, User currentUser) {
        TourPackage tourPackage = getEntity(id);
        assertCanManage(tourPackage, currentUser);
        assertStatus(tourPackage, PackageStatus.ACTIVE, "deactivated");

        tourPackage.setStatus(PackageStatus.INACTIVE);
        return toResponse(tourPackageRepository.save(tourPackage));
    }

    @Override
    public TourPackageResponseDto reactivatePackage(Long id, User currentUser) {
        TourPackage tourPackage = getEntity(id);
        assertCanManage(tourPackage, currentUser);
        assertStatus(tourPackage, PackageStatus.INACTIVE, "reactivated");

        tourPackage.setStatus(PackageStatus.ACTIVE);
        return toResponse(tourPackageRepository.save(tourPackage));
    }

    @Override
    public TourPackageResponseDto archivePackage(Long id, User currentUser) {
        TourPackage tourPackage = getEntity(id);
        assertCanManage(tourPackage, currentUser);

        if (tourPackage.getStatus() == PackageStatus.ARCHIVED) {
            throw new InvalidStatusTransitionException("This package is already archived");
        }

        tourPackage.setStatus(PackageStatus.ARCHIVED);
        return toResponse(tourPackageRepository.save(tourPackage));
    }

    @Override
    public TourPackageResponseDto getPackageById(Long id) {
        return toResponse(getEntity(id));
    }

    @Override
    public List<TourPackageResponseDto> getAllActivePackages() {
        return tourPackageRepository.findByStatus(PackageStatus.ACTIVE).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<TourPackageResponseDto> getPendingApprovalPackages() {
        return tourPackageRepository.findByStatus(PackageStatus.PENDING_APPROVAL).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<TourPackageResponseDto> getPackagesByCreator(Long userId) {
        return tourPackageRepository.findByCreatedById(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<TourPackageResponseDto> searchPackages(Long destinationId, BigDecimal minPrice, BigDecimal maxPrice) {
        return tourPackageRepository.search(PackageStatus.ACTIVE, destinationId, minPrice, maxPrice).stream()
                .map(this::toResponse)
                .toList();
    }

    private TourPackage getEntity(Long id) {
        return tourPackageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tour package not found with id: " + id));
    }

    private void assertCanManage(TourPackage tourPackage, User currentUser) {
        boolean isOwner = tourPackage.getCreatedBy().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to manage this package");
        }
    }

    private void assertStatus(TourPackage tourPackage, PackageStatus required, String action) {
        if (tourPackage.getStatus() != required) {
            throw new InvalidStatusTransitionException(
                    "Only " + required + " packages can be " + action + ", but this package is "
                            + tourPackage.getStatus());
        }
    }

    /**
     * Lightweight destination view embedded in a package response — id +
     * name + region + status, no active-listing counts (not needed here,
     * and cheaper to skip the count queries per package).
     */
    private DestinationResponseDto toDestinationSummary(Destination destination) {
        return DestinationResponseDto.builder()
                .id(destination.getId())
                .name(destination.getName())
                .region(destination.getRegion())
                .status(destination.getStatus())
                .build();
    }

    private TourPackageResponseDto toResponse(TourPackage tourPackage) {
        return TourPackageResponseDto.builder()
                .id(tourPackage.getId())
                .title(tourPackage.getTitle())
                .description(tourPackage.getDescription())
                .destination(toDestinationSummary(tourPackage.getDestination()))
                .durationDays(tourPackage.getDurationDays())
                .price(tourPackage.getPrice())
                .maxCapacity(tourPackage.getMaxCapacity())
                .status(tourPackage.getStatus())
                .createdById(tourPackage.getCreatedBy().getId())
                .createdByName(tourPackage.getCreatedBy().getName())
                .createdAt(tourPackage.getCreatedAt())
                .build();
    }

}
