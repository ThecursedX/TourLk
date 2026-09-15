package com.tourlk.service;

import com.tourlk.dto.DestinationRequestDto;
import com.tourlk.dto.DestinationResponseDto;
import com.tourlk.entity.Destination;
import com.tourlk.enums.AccommodationStatus;
import com.tourlk.enums.DestinationStatus;
import com.tourlk.enums.PackageStatus;
import com.tourlk.exception.DestinationInactiveException;
import com.tourlk.exception.DuplicateDestinationException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.AccommodationRepository;
import com.tourlk.repo.DestinationRepository;
import com.tourlk.repo.TourPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DestinationServiceImpl implements DestinationService {

    private final DestinationRepository destinationRepository;
    private final TourPackageRepository tourPackageRepository;
    private final AccommodationRepository accommodationRepository;

    @Override
    public DestinationResponseDto createDestination(DestinationRequestDto request) {
        assertNameAvailable(request.getName(), null);

        // අලුත් ෆීල්ඩ්ස් ටික මෙතනට ඇතුළත් කර ඇත
        Destination destination = Destination.builder()
                .name(request.getName().trim())
                .region(request.getRegion())
                .district(request.getDistrict())
                .category(request.getCategory())
                .bestTimeToVisit(request.getBestTimeToVisit())
                .imageUrl(request.getImageUrl())
                .description(request.getDescription())
                .status(DestinationStatus.ACTIVE)
                .build();

        return toResponse(destinationRepository.save(destination), true);
    }

    @Override
    public DestinationResponseDto updateDestination(Long id, DestinationRequestDto request) {
        Destination destination = getEntity(id);
        assertNameAvailable(request.getName(), id);

        // අලුත් ෆීල්ඩ්ස් ටික Update වෙන්න මෙතනටත් ඇතුළත් කර ඇත
        destination.setName(request.getName().trim());
        destination.setRegion(request.getRegion());
        destination.setDistrict(request.getDistrict());
        destination.setCategory(request.getCategory());
        destination.setBestTimeToVisit(request.getBestTimeToVisit());
        destination.setImageUrl(request.getImageUrl());
        destination.setDescription(request.getDescription());

        return toResponse(destinationRepository.save(destination), true);
    }

    @Override
    public DestinationResponseDto deactivateDestination(Long id) {
        Destination destination = getEntity(id);
        destination.setStatus(DestinationStatus.INACTIVE);
        return toResponse(destinationRepository.save(destination), true);
    }

    @Override
    public DestinationResponseDto reactivateDestination(Long id) {
        Destination destination = getEntity(id);
        destination.setStatus(DestinationStatus.ACTIVE);
        return toResponse(destinationRepository.save(destination), true);
    }

    @Override
    public List<DestinationResponseDto> getAllActive() {
        return destinationRepository.findByStatus(DestinationStatus.ACTIVE).stream()
                .map(d -> toResponse(d, false))
                .toList();
    }

    @Override
    public List<DestinationResponseDto> getAll() {
        return destinationRepository.findAll().stream()
                .map(d -> toResponse(d, true))
                .toList();
    }

    @Override
    public DestinationResponseDto getById(Long id) {
        return toResponse(getEntity(id), true);
    }

    @Override
    public List<DestinationResponseDto> searchByName(String query) {
        return destinationRepository.findByNameContainingIgnoreCase(query == null ? "" : query).stream()
                .filter(d -> d.getStatus() == DestinationStatus.ACTIVE)
                .map(d -> toResponse(d, false))
                .toList();
    }

    @Override
    public List<DestinationResponseDto> getByRegion(String region) {
        return destinationRepository.findByRegion(region).stream()
                .filter(d -> d.getStatus() == DestinationStatus.ACTIVE)
                .map(d -> toResponse(d, false))
                .toList();
    }

    @Override
    public Destination requireSelectableDestination(Long id) {
        Destination destination = getEntity(id);
        if (destination.getStatus() != DestinationStatus.ACTIVE) {
            throw new DestinationInactiveException(
                    "Destination '" + destination.getName() + "' is inactive and cannot be selected");
        }
        return destination;
    }

    private Destination getEntity(Long id) {
        return destinationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found with id: " + id));
    }

    private void assertNameAvailable(String name, Long selfId) {
        if (name == null) {
            return;
        }
        destinationRepository.findByNameIgnoreCase(name.trim())
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new DuplicateDestinationException(
                            "A destination named '" + existing.getName() + "' already exists");
                });
    }

    private DestinationResponseDto toResponse(Destination destination, boolean withCounts) {
        // UI එකට යවනකොට අලුත් ඩේටා ටිකත් යවන්න මේ ටිකත් අප්ඩේට් කළා
        DestinationResponseDto.DestinationResponseDtoBuilder builder = DestinationResponseDto.builder()
                .id(destination.getId())
                .name(destination.getName())
                .region(destination.getRegion())
                .district(destination.getDistrict())
                .category(destination.getCategory())
                .bestTimeToVisit(destination.getBestTimeToVisit())
                .imageUrl(destination.getImageUrl())
                .description(destination.getDescription())
                .status(destination.getStatus())
                .createdAt(destination.getCreatedAt());

        if (withCounts) {
            builder.activePackageCount(
                    tourPackageRepository.countByDestinationIdAndStatus(destination.getId(), PackageStatus.ACTIVE));
            builder.activeAccommodationCount(
                    accommodationRepository.countByLocationIdAndStatus(destination.getId(), AccommodationStatus.ACTIVE));
        }

        return builder.build();
    }

}