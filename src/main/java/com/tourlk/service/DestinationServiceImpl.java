package com.tourlk.service;

import com.tourlk.dto.DestinationRequestDto;
import com.tourlk.dto.DestinationResponseDto;
import com.tourlk.entity.Destination;
import com.tourlk.enums.AccommodationStatus;
import com.tourlk.enums.DestinationStatus;
import com.tourlk.enums.PackageStatus;
import com.tourlk.enums.Province;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.DestinationInactiveException;
import com.tourlk.exception.DuplicateDestinationException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.AccommodationRepository;
import com.tourlk.repo.DestinationRepository;
import com.tourlk.repo.TourPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class DestinationServiceImpl implements DestinationService {

    private static final int MAX_IMAGES = 10;

    /** Shown as suggestions even before any destination uses them. */
    private static final List<String> STARTER_CATEGORIES = List.of(
            "Beach", "Cultural", "Historical", "Wildlife", "Hill Country",
            "Adventure", "Religious", "Nature", "City");

    private final DestinationRepository destinationRepository;
    private final TourPackageRepository tourPackageRepository;
    private final AccommodationRepository accommodationRepository;

    @Override
    public DestinationResponseDto createDestination(DestinationRequestDto request) {
        assertNameAvailable(request.getName(), null);

        Destination destination = Destination.builder()
                .status(DestinationStatus.ACTIVE)
                .build();
        applyRequest(destination, request);

        return toResponse(destinationRepository.save(destination), true);
    }

    @Override
    public DestinationResponseDto updateDestination(Long id, DestinationRequestDto request) {
        Destination destination = getEntity(id);
        assertNameAvailable(request.getName(), id);

        applyRequest(destination, request);

        return toResponse(destinationRepository.save(destination), true);
    }

    @Override
    public DestinationResponseDto deactivateDestination(Long id) {
        Destination destination = getEntity(id);
        // Idempotent-ish: deactivating an already-inactive destination is a no-op.
        // Existing TourPackages/Accommodations keep their reference — only new
        // ones are blocked from selecting it (see requireSelectableDestination).
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
        // Public-facing: active destinations only.
        return destinationRepository.findByNameContainingIgnoreCase(query == null ? "" : query).stream()
                .filter(d -> d.getStatus() == DestinationStatus.ACTIVE)
                .map(d -> toResponse(d, false))
                .toList();
    }

    @Override
    public List<DestinationResponseDto> getByProvince(Province province) {
        // Public-facing: active destinations only. Rows saved before provinces
        // existed have a null province column, so also match on the legacy region.
        return destinationRepository.findAll().stream()
                .filter(d -> d.getStatus() == DestinationStatus.ACTIVE)
                .filter(d -> d.resolveProvince() == province)
                .map(d -> toResponse(d, false))
                .toList();
    }

    @Override
    public List<String> getCategorySuggestions() {
        // Case-insensitive de-duplication; the first spelling seen wins, and
        // the starter list goes in first so its capitalisation is preferred.
        Map<String, String> byLowerCase = new TreeMap<>();
        for (String category : STARTER_CATEGORIES) {
            byLowerCase.putIfAbsent(category.toLowerCase(Locale.ROOT), category);
        }
        for (String category : destinationRepository.findDistinctCategories()) {
            if (category != null && !category.isBlank()) {
                byLowerCase.putIfAbsent(category.trim().toLowerCase(Locale.ROOT), category.trim());
            }
        }
        // TreeMap iterates in key (lower-case) order, so this is already sorted.
        return List.copyOf(byLowerCase.values());
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

    /**
     * Copies the editable fields from the request onto the entity, validating
     * what bean validation can't: the district must belong to the province,
     * and image URLs must be http(s) and few enough. The legacy region
     * column is kept in step with the province.
     */
    private void applyRequest(Destination destination, DestinationRequestDto request) {
        Province province = request.getProvince();
        String district = province.canonicalDistrict(request.getDistrict());
        if (district == null) {
            throw new BadRequestException(
                    "'" + request.getDistrict().trim() + "' is not a district of " + province.getDisplayName());
        }

        destination.setName(request.getName().trim());
        destination.setDescription(blankToNull(request.getDescription()));
        destination.setProvince(province);
        destination.setRegion(province.getDisplayName());
        destination.setDistrict(district);
        destination.setCategory(request.getCategory().trim());
        destination.setBestTimeToVisit(blankToNull(request.getBestTimeToVisit()));

        List<String> imageUrls = cleanImageUrls(request.getImageUrls());
        if (destination.getImageUrls() == null) {
            destination.setImageUrls(new ArrayList<>());
        }
        destination.getImageUrls().clear();
        destination.getImageUrls().addAll(imageUrls);
    }

    private List<String> cleanImageUrls(List<String> urls) {
        List<String> cleaned = new ArrayList<>();
        if (urls == null) {
            return cleaned;
        }
        for (String url : urls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            String trimmed = url.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                throw new BadRequestException("Image URLs must start with http:// or https://");
            }
            cleaned.add(trimmed);
        }
        if (cleaned.size() > MAX_IMAGES) {
            throw new BadRequestException("A destination can have at most " + MAX_IMAGES + " images");
        }
        return cleaned;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
        DestinationResponseDto.DestinationResponseDtoBuilder builder = DestinationResponseDto.builder()
                .id(destination.getId())
                .name(destination.getName())
                .region(destination.resolveRegion())
                .description(destination.getDescription())
                .province(destination.resolveProvince())
                .district(destination.getDistrict())
                .category(destination.getCategory())
                .bestTimeToVisit(destination.getBestTimeToVisit())
                .imageUrls(destination.getImageUrls() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(destination.getImageUrls()))
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
