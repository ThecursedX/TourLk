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
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.AccommodationRepository;
import com.tourlk.repo.DestinationRepository;
import com.tourlk.repo.TourPackageRepository;
import com.tourlk.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class DestinationServiceImpl implements DestinationService {

    private static final int MAX_IMAGES = 10;
    private static final double DEFAULT_NEARBY_RADIUS_KM = 50;
    private static final double MAX_NEARBY_RADIUS_KM = 20_000;

    /** Shown in public browsing; TEMPORARILY_CLOSED stays visible (with a warning). */
    private static final Set<DestinationStatus> PUBLIC = EnumSet.of(
            DestinationStatus.PUBLISHED, DestinationStatus.TEMPORARILY_CLOSED);

    /** Hidden from non-admins even on the detail endpoint. */
    private static final Set<DestinationStatus> ADMIN_ONLY = EnumSet.of(
            DestinationStatus.DRAFT, DestinationStatus.PENDING_REVIEW, DestinationStatus.ARCHIVED);

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
                .status(Boolean.TRUE.equals(request.getSaveAsDraft())
                        ? DestinationStatus.DRAFT : DestinationStatus.PUBLISHED)
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
    public DestinationResponseDto submitForReview(Long id) {
        Destination destination = getEntity(id);
        assertStatus(destination, EnumSet.of(DestinationStatus.DRAFT), "submitted for review");

        destination.setStatus(DestinationStatus.PENDING_REVIEW);
        return toResponse(destinationRepository.save(destination), true);
    }

    @Override
    public DestinationResponseDto publishDestination(Long id) {
        Destination destination = getEntity(id);
        assertStatus(destination, EnumSet.of(DestinationStatus.DRAFT, DestinationStatus.PENDING_REVIEW), "published");

        destination.setStatus(DestinationStatus.PUBLISHED);
        return toResponse(destinationRepository.save(destination), true);
    }

    @Override
    public DestinationResponseDto closeTemporarily(Long id, String reason, LocalDate until) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A closure reason is required");
        }
        if (reason.trim().length() > 500) {
            throw new BadRequestException("Closure reason must be at most 500 characters");
        }
        if (until != null && until.isBefore(LocalDate.now())) {
            throw new BadRequestException("The closure end date cannot be in the past");
        }

        Destination destination = getEntity(id);
        assertStatus(destination,
                EnumSet.of(DestinationStatus.PUBLISHED, DestinationStatus.TEMPORARILY_CLOSED), "closed");

        destination.setStatus(DestinationStatus.TEMPORARILY_CLOSED);
        destination.setClosureReason(reason.trim());
        destination.setClosureUntil(until);
        return toResponse(destinationRepository.save(destination), true);
    }

    @Override
    public DestinationResponseDto reopenDestination(Long id) {
        Destination destination = getEntity(id);
        assertStatus(destination, EnumSet.of(DestinationStatus.TEMPORARILY_CLOSED), "reopened");

        reopen(destination);
        return toResponse(destinationRepository.save(destination), true);
    }

    @Override
    public int reopenExpiredClosures(LocalDate today) {
        List<Destination> expired =
                destinationRepository.findByStatusAndClosureUntilBefore(DestinationStatus.TEMPORARILY_CLOSED, today);
        expired.forEach(destination -> {
            reopen(destination);
            destinationRepository.save(destination);
        });
        return expired.size();
    }

    @Override
    public DestinationResponseDto archiveDestination(Long id) {
        Destination destination = getEntity(id);
        if (destination.getStatus() == DestinationStatus.ARCHIVED) {
            throw new InvalidStatusTransitionException("This destination is already archived");
        }

        destination.setStatus(DestinationStatus.ARCHIVED);
        destination.setClosureReason(null);
        destination.setClosureUntil(null);
        return toResponse(destinationRepository.save(destination), true);
    }

    @Override
    public DestinationResponseDto deactivateDestination(Long id) {
        Destination destination = getEntity(id);
        // Idempotent-ish: deactivating an already-inactive destination is a no-op.
        // Existing TourPackages/Accommodations keep their reference — only new
        // ones are blocked from selecting it (see requireSelectableDestination).
        // An archived destination stays archived.
        if (destination.getStatus() == DestinationStatus.ARCHIVED) {
            throw new InvalidStatusTransitionException("An archived destination cannot be deactivated");
        }
        destination.setStatus(DestinationStatus.INACTIVE);
        destination.setClosureReason(null);
        destination.setClosureUntil(null);
        return toResponse(destinationRepository.save(destination), true);
    }

    @Override
    public DestinationResponseDto reactivateDestination(Long id) {
        Destination destination = getEntity(id);
        assertStatus(destination, EnumSet.of(DestinationStatus.INACTIVE, DestinationStatus.ARCHIVED), "reactivated");

        destination.setStatus(DestinationStatus.PUBLISHED);
        return toResponse(destinationRepository.save(destination), true);
    }

    @Override
    public List<DestinationResponseDto> getAllActive() {
        return destinationRepository.findByStatusIn(PUBLIC).stream()
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
    public DestinationResponseDto getById(Long id, boolean isAdmin) {
        Destination destination = getEntity(id);
        if (!isAdmin && ADMIN_ONLY.contains(destination.getStatus())) {
            throw new ResourceNotFoundException("Destination not found with id: " + id);
        }
        return toResponse(destination, true);
    }

    @Override
    public List<DestinationResponseDto> searchNearby(String nearby, Double radiusKm) {
        double[] point = parsePoint(nearby);
        double radius = radiusKm == null ? DEFAULT_NEARBY_RADIUS_KM : radiusKm;
        if (!(radius > 0) || radius > MAX_NEARBY_RADIUS_KM) {
            throw new BadRequestException("radiusKm must be greater than 0 and at most " + (int) MAX_NEARBY_RADIUS_KM);
        }

        return destinationRepository.findByStatusIn(PUBLIC).stream()
                .filter(d -> d.getLatitude() != null && d.getLongitude() != null)
                .map(d -> Map.entry(d, GeoUtils.haversineKm(point[0], point[1], d.getLatitude(), d.getLongitude())))
                .filter(entry -> entry.getValue() <= radius)
                .sorted(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                .map(entry -> {
                    DestinationResponseDto dto = toResponse(entry.getKey(), false);
                    dto.setDistanceKm(Math.round(entry.getValue() * 10.0) / 10.0);
                    return dto;
                })
                .toList();
    }

    private double[] parsePoint(String nearby) {
        String[] parts = nearby == null ? new String[0] : nearby.split(",");
        if (parts.length != 2) {
            throw new BadRequestException("nearby must be in the form 'lat,lng'");
        }
        double lat;
        double lng;
        try {
            lat = Double.parseDouble(parts[0].trim());
            lng = Double.parseDouble(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("nearby must be in the form 'lat,lng'");
        }
        if (Double.isNaN(lat) || Double.isNaN(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new BadRequestException("nearby latitude must be within [-90, 90] and longitude within [-180, 180]");
        }
        return new double[] {lat, lng};
    }

    @Override
    public List<DestinationResponseDto> searchByName(String query) {
        // Public-facing: active destinations only.
        return destinationRepository.findByNameContainingIgnoreCase(query == null ? "" : query).stream()
                .filter(d -> PUBLIC.contains(d.getStatus()))
                .map(d -> toResponse(d, false))
                .toList();
    }

    @Override
    public List<DestinationResponseDto> getByProvince(Province province) {
        // Public-facing: active destinations only. Rows saved before provinces
        // existed have a null province column, so also match on the legacy region.
        return destinationRepository.findAll().stream()
                .filter(d -> PUBLIC.contains(d.getStatus()))
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
        if (!PUBLIC.contains(destination.getStatus())) {
            throw new DestinationInactiveException(
                    "Destination '" + destination.getName() + "' is " + destination.getStatus()
                            + " and cannot be selected");
        }
        return destination;
    }

    private void reopen(Destination destination) {
        destination.setStatus(DestinationStatus.PUBLISHED);
        destination.setClosureReason(null);
        destination.setClosureUntil(null);
    }

    private void assertStatus(Destination destination, Set<DestinationStatus> allowed, String action) {
        if (!allowed.contains(destination.getStatus())) {
            throw new InvalidStatusTransitionException(
                    "Only " + allowed + " destinations can be " + action + ", but this destination is "
                            + destination.getStatus());
        }
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
        destination.setOpeningHours(blankToNull(request.getOpeningHours()));
        destination.setEntryFee(request.getEntryFee());
        destination.setVisitorRules(blankToNull(request.getVisitorRules()));
        if ((request.getLatitude() == null) != (request.getLongitude() == null)) {
            throw new BadRequestException("Latitude and longitude must be provided together");
        }
        destination.setLatitude(request.getLatitude());
        destination.setLongitude(request.getLongitude());

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
                .openingHours(destination.getOpeningHours())
                .entryFee(destination.getEntryFee())
                .visitorRules(destination.getVisitorRules())
                .latitude(destination.getLatitude())
                .longitude(destination.getLongitude())
                .closureReason(destination.getClosureReason())
                .closureUntil(destination.getClosureUntil())
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
