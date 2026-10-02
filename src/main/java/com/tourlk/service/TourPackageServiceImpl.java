package com.tourlk.service;

import com.tourlk.dto.DestinationResponseDto;
import com.tourlk.dto.ItineraryDayRequestDto;
import com.tourlk.dto.ItineraryDayResponseDto;
import com.tourlk.dto.PackageSearchCriteria;
import com.tourlk.dto.TourPackageRequestDto;
import com.tourlk.dto.TourPackageResponseDto;
import com.tourlk.entity.Destination;
import com.tourlk.entity.ItineraryDay;
import com.tourlk.entity.TourPackage;
import com.tourlk.entity.User;
import com.tourlk.enums.BookingStatus;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.PackageSort;
import com.tourlk.enums.PackageStatus;
import com.tourlk.enums.ReviewStatus;
import com.tourlk.enums.ReviewableType;
import com.tourlk.enums.Role;
import com.tourlk.enums.VerificationStatus;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.ChangesRequireConfirmationException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.LicenceNotVerifiedException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.BookingRepository;
import com.tourlk.repo.ItineraryDayRepository;
import com.tourlk.repo.PackageDepartureRepository;
import com.tourlk.repo.ReviewRepository;
import com.tourlk.repo.ReviewRepository.RatingAggregate;
import com.tourlk.repo.TourPackageRepository;
import com.tourlk.repo.TourPackageSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TourPackageServiceImpl implements TourPackageService {

    private static final int MAX_IMAGES = 10;
    private static final int MAX_QUERY_LENGTH = 100;

    /** Highest rating first, then most reviewed, then id for a stable order. */
    private static final Comparator<TourPackageResponseDto> BY_RATING =
            Comparator.comparingDouble(TourPackageResponseDto::getAverageRating).reversed()
                    .thenComparing(Comparator.comparingLong(TourPackageResponseDto::getReviewCount).reversed())
                    .thenComparing(TourPackageResponseDto::getId);

    private final TourPackageRepository tourPackageRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final PackageDepartureRepository departureRepository;
    private final ReviewRepository reviewRepository;
    private final BudgetTierPolicy budgetTierPolicy;
    private final BookingRepository bookingRepository;
    private final DestinationService destinationService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public TourPackageResponseDto createPackage(TourPackageRequestDto request, User currentUser) {
        assertLicenceVerifiedIfGuide(currentUser);
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
        applyLists(tourPackage, request);
        validateItineraryDays(request.getItineraryDays(), tourPackage.getDurationDays());

        tourPackage = tourPackageRepository.save(tourPackage);
        replaceItineraryDays(tourPackage, request.getItineraryDays());

        return toResponse(tourPackage);
    }

    @Override
    @Transactional
    public TourPackageResponseDto updatePackage(Long id, TourPackageRequestDto request, User currentUser,
                                                boolean confirmChanges) {
        TourPackage tourPackage = getEntity(id);
        assertCanManage(tourPackage, currentUser);

        if (tourPackage.getStatus() != PackageStatus.DRAFT && tourPackage.getStatus() != PackageStatus.ACTIVE) {
            throw new InvalidStatusTransitionException(
                    "A package can only be edited while it is DRAFT or ACTIVE, not " + tourPackage.getStatus());
        }
        if (!confirmChanges) {
            assertNoUnconfirmedBookingImpact(tourPackage, request);
        }

        tourPackage.setTitle(request.getTitle());
        tourPackage.setDescription(request.getDescription());
        tourPackage.setDestination(destinationService.requireSelectableDestination(request.getDestinationId()));
        tourPackage.setDurationDays(request.getDurationDays());
        tourPackage.setPrice(request.getPrice());
        tourPackage.setMaxCapacity(request.getMaxCapacity());
        applyLists(tourPackage, request);
        validateItineraryDays(request.getItineraryDays(), tourPackage.getDurationDays());

        tourPackage = tourPackageRepository.save(tourPackage);
        replaceItineraryDays(tourPackage, request.getItineraryDays());

        return toResponse(tourPackage);
    }

    @Override
    public TourPackageResponseDto submitForApproval(Long id, User currentUser) {
        assertLicenceVerifiedIfGuide(currentUser);
        TourPackage tourPackage = getEntity(id);
        assertCanManage(tourPackage, currentUser);
        assertStatus(tourPackage, PackageStatus.DRAFT, "submitted for approval");

        tourPackage.setStatus(PackageStatus.PENDING_APPROVAL);
        tourPackage.setRejectionReason(null);
        tourPackage = tourPackageRepository.save(tourPackage);

        notificationService.notifyAdmins(NotificationType.PACKAGE_SUBMITTED, "Package submitted for approval",
                tourPackage.getCreatedBy().getName() + " submitted \"" + tourPackage.getTitle() + "\" for approval",
                "/admin/packages?status=PENDING_APPROVAL");

        return toResponse(tourPackage);
    }

    @Override
    public TourPackageResponseDto approvePackage(Long id) {
        TourPackage tourPackage = getEntity(id);
        assertStatus(tourPackage, PackageStatus.PENDING_APPROVAL, "approved");

        tourPackage.setStatus(PackageStatus.ACTIVE);
        tourPackage = tourPackageRepository.save(tourPackage);

        notificationService.notify(tourPackage.getCreatedBy(), NotificationType.PACKAGE_APPROVED,
                "Package approved", "Your package \"" + tourPackage.getTitle() + "\" has been approved and is now live",
                "/packages/" + tourPackage.getId());

        return toResponse(tourPackage);
    }

    @Override
    public TourPackageResponseDto rejectPackage(Long id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A rejection reason is required");
        }
        TourPackage tourPackage = getEntity(id);
        assertStatus(tourPackage, PackageStatus.PENDING_APPROVAL, "rejected");

        tourPackage.setStatus(PackageStatus.DRAFT);
        tourPackage.setRejectionReason(reason.trim());
        tourPackage = tourPackageRepository.save(tourPackage);

        notificationService.notify(tourPackage.getCreatedBy(), NotificationType.PACKAGE_REJECTED,
                "Package rejected", "Your package \"" + tourPackage.getTitle() + "\" was not approved. Reason: "
                        + tourPackage.getRejectionReason(),
                "/packages/" + tourPackage.getId() + "/edit");

        return toResponse(tourPackage);
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
    public TourPackageResponseDto getPackageById(Long id, User currentUser) {
        TourPackage tourPackage = getEntity(id);
        if (tourPackage.getStatus() != PackageStatus.ACTIVE && !canManage(tourPackage, currentUser)) {
            // Same message as a missing id, so a hidden package doesn't reveal that it exists.
            throw new ResourceNotFoundException("Tour package not found with id: " + id);
        }
        return toResponse(tourPackage);
    }

    @Override
    public List<TourPackageResponseDto> getAllPackagesForAdmin(PackageStatus status) {
        Sort newestFirst = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        return toResponses(status == null
                ? tourPackageRepository.findAll(newestFirst)
                : tourPackageRepository.findByStatus(status, newestFirst));
    }

    @Override
    public List<TourPackageResponseDto> browsePackages(PackageSearchCriteria criteria) {
        validateCriteria(criteria);

        Specification<TourPackage> spec = Specification
                .where(TourPackageSpecifications.hasStatus(PackageStatus.ACTIVE))
                .and(TourPackageSpecifications.inDestination(criteria.getDestinationId()))
                .and(TourPackageSpecifications.priceBetween(criteria.getMinPrice(), criteria.getMaxPrice()))
                .and(TourPackageSpecifications.durationBetween(criteria.getMinDays(), criteria.getMaxDays()))
                .and(TourPackageSpecifications.textContains(criteria.getQ()))
                .and(TourPackageSpecifications.inBudgetTier(criteria.getBudgetTier(),
                        budgetTierPolicy.standardMinPerDay(), budgetTierPolicy.luxuryMinPerDay()))
                .and(TourPackageSpecifications.departsOnOrAfter(criteria.getTravelDate()));

        List<TourPackageResponseDto> results =
                toResponses(tourPackageRepository.findAll(spec, databaseSort(criteria.getSort())));

        // Rating is an aggregate over another table; the listing is small and
        // unpaginated, so it's sorted here after the one batched rating query.
        if (criteria.getSort() == PackageSort.RATING) {
            return results.stream().sorted(BY_RATING).toList();
        }
        return results;
    }

    @Override
    public List<TourPackageResponseDto> getPendingApprovalPackages() {
        return toResponses(tourPackageRepository.findByStatus(PackageStatus.PENDING_APPROVAL));
    }

    @Override
    public List<TourPackageResponseDto> getPackagesByCreator(Long userId) {
        return toResponses(tourPackageRepository.findByCreatedById(userId));
    }

    private void validateCriteria(PackageSearchCriteria criteria) {
        BigDecimal minPrice = criteria.getMinPrice();
        BigDecimal maxPrice = criteria.getMaxPrice();
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)) {
            throw new BadRequestException("Prices cannot be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("minPrice cannot be greater than maxPrice");
        }
        Integer minDays = criteria.getMinDays();
        Integer maxDays = criteria.getMaxDays();
        if ((minDays != null && minDays < 1) || (maxDays != null && maxDays < 1)) {
            throw new BadRequestException("Durations must be at least 1 day");
        }
        if (minDays != null && maxDays != null && minDays > maxDays) {
            throw new BadRequestException("minDays cannot be greater than maxDays");
        }
        if (criteria.getQ() != null && criteria.getQ().trim().length() > MAX_QUERY_LENGTH) {
            throw new BadRequestException("Search text must be at most " + MAX_QUERY_LENGTH + " characters");
        }
    }

    /** RATING (and no sort) return Sort.unsorted() — rating is ordered in memory by browsePackages. */
    private Sort databaseSort(PackageSort sort) {
        if (sort == null) {
            return Sort.unsorted();
        }
        return switch (sort) {
            case PRICE_ASC -> Sort.by(Sort.Order.asc("price"), Sort.Order.asc("id"));
            case PRICE_DESC -> Sort.by(Sort.Order.desc("price"), Sort.Order.asc("id"));
            case DURATION -> Sort.by(Sort.Order.asc("durationDays"), Sort.Order.asc("price"), Sort.Order.asc("id"));
            case NEWEST -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            case RATING -> Sort.unsorted();
        };
    }

    private TourPackage getEntity(Long id) {
        return tourPackageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tour package not found with id: " + id));
    }

    private void assertCanManage(TourPackage tourPackage, User currentUser) {
        if (!canManage(tourPackage, currentUser)) {
            throw new AccessDeniedException("You do not have permission to manage this package");
        }
    }

    private void assertLicenceVerifiedIfGuide(User currentUser) {
        if (currentUser.getRole() == Role.GUIDE && currentUser.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new LicenceNotVerifiedException(
                    "You must be a verified guide before you can create or submit tour packages");
        }
    }

    /** Owner or admin; false for an anonymous (null) user. */
    private boolean canManage(TourPackage tourPackage, User currentUser) {
        if (currentUser == null) {
            return false;
        }
        return currentUser.getRole() == Role.ADMIN
                || tourPackage.getCreatedBy().getId().equals(currentUser.getId());
    }

    /**
     * Price, duration and capacity are what a tourist booked against. On an
     * ACTIVE package with upcoming (non-cancelled, non-completed) bookings,
     * changing any of them must be confirmed explicitly. Existing bookings
     * keep their stored totalPrice either way.
     */
    private void assertNoUnconfirmedBookingImpact(TourPackage tourPackage, TourPackageRequestDto request) {
        if (tourPackage.getStatus() != PackageStatus.ACTIVE) {
            return;
        }
        List<String> changedFields = new ArrayList<>();
        if (request.getPrice() != null && tourPackage.getPrice().compareTo(request.getPrice()) != 0) {
            changedFields.add("price");
        }
        if (request.getDurationDays() != null && tourPackage.getDurationDays() != request.getDurationDays()) {
            changedFields.add("durationDays");
        }
        if (request.getMaxCapacity() != null && tourPackage.getMaxCapacity() != request.getMaxCapacity()) {
            changedFields.add("maxCapacity");
        }
        if (changedFields.isEmpty()) {
            return;
        }
        long upcoming = bookingRepository.countUpcomingActiveBookings(
                tourPackage.getId(), LocalDate.now(), BookingStatus.TERMINAL);
        if (upcoming > 0) {
            throw new ChangesRequireConfirmationException(changedFields, upcoming);
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
     * Copies inclusions/exclusions/image URLs onto the entity, validating
     * what bean validation can't: image URLs must be http(s) and few
     * enough — same rule as {@code Destination#imageUrls}.
     */
    private void applyLists(TourPackage tourPackage, TourPackageRequestDto request) {
        replaceCollection(tourPackage.getInclusions(), cleanStrings(request.getInclusions()));
        replaceCollection(tourPackage.getExclusions(), cleanStrings(request.getExclusions()));
        replaceCollection(tourPackage.getImageUrls(), cleanImageUrls(request.getImageUrls()));
    }

    private void replaceCollection(List<String> target, List<String> values) {
        target.clear();
        target.addAll(values);
    }

    private List<String> cleanStrings(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                cleaned.add(value.trim());
            }
        }
        return cleaned;
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
            throw new BadRequestException("A tour package can have at most " + MAX_IMAGES + " images");
        }
        return cleaned;
    }

    /**
     * Replaces the package's whole itinerary (delete then re-insert) rather
     * than diffing day-by-day — simpler, and itinerary days have no
     * identity that anything else references. Day numbers are validated
     * by {@link #validateItineraryDays} before the package itself is
     * saved, so a bad itinerary never persists a half-updated package.
     */
    private void replaceItineraryDays(TourPackage tourPackage, List<ItineraryDayRequestDto> requestDays) {
        List<ItineraryDayRequestDto> days = requestDays == null ? List.of() : requestDays;

        itineraryDayRepository.deleteByTourPackageId(tourPackage.getId());

        List<ItineraryDay> entities = days.stream()
                .map(day -> ItineraryDay.builder()
                        .tourPackage(tourPackage)
                        .dayNumber(day.getDayNumber())
                        .title(day.getTitle().trim())
                        .description(blankToNull(day.getDescription()))
                        .placesToVisit(cleanStrings(day.getPlacesToVisit()))
                        .build())
                .toList();
        itineraryDayRepository.saveAll(entities);
    }

    private void validateItineraryDays(List<ItineraryDayRequestDto> requestDays, int durationDays) {
        List<ItineraryDayRequestDto> days = requestDays == null ? List.of() : requestDays;
        Set<Integer> seenDayNumbers = new HashSet<>();
        for (ItineraryDayRequestDto day : days) {
            int dayNumber = day.getDayNumber();
            if (dayNumber < 1 || dayNumber > durationDays) {
                throw new BadRequestException(
                        "Itinerary day " + dayNumber + " is out of range for a " + durationDays + "-day package");
            }
            if (!seenDayNumbers.add(dayNumber)) {
                throw new BadRequestException("Itinerary day numbers must be unique (duplicate: " + dayNumber + ")");
            }
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
                .region(destination.resolveRegion())
                .province(destination.resolveProvince())
                .district(destination.getDistrict())
                .status(destination.getStatus())
                .build();
    }

    private ItineraryDayResponseDto toItineraryResponse(ItineraryDay day) {
        return ItineraryDayResponseDto.builder()
                .id(day.getId())
                .dayNumber(day.getDayNumber())
                .title(day.getTitle())
                .description(day.getDescription())
                .placesToVisit(day.getPlacesToVisit() == null ? new ArrayList<>() : new ArrayList<>(day.getPlacesToVisit()))
                .build();
    }

    private TourPackageResponseDto toResponse(TourPackage tourPackage) {
        return toResponses(List.of(tourPackage)).get(0);
    }

    /**
     * Maps packages with a fixed number of queries regardless of how many
     * there are: one each for itinerary days, departure flags and ratings,
     * all batched by package id (instead of one of each per package).
     */
    private List<TourPackageResponseDto> toResponses(List<TourPackage> packages) {
        if (packages.isEmpty()) {
            return List.of();
        }
        List<Long> ids = packages.stream().map(TourPackage::getId).toList();

        Map<Long, List<ItineraryDayResponseDto>> itineraries =
                itineraryDayRepository.findByTourPackageIdInOrderByDayNumberAsc(ids).stream()
                        .collect(Collectors.groupingBy(day -> day.getTourPackage().getId(),
                                Collectors.mapping(this::toItineraryResponse, Collectors.toList())));
        Set<Long> withDepartures = new HashSet<>(departureRepository.findPackageIdsWithDepartures(ids));
        Map<Long, RatingAggregate> ratings =
                reviewRepository.aggregateRatings(ReviewableType.TOUR_PACKAGE, ids, ReviewStatus.HIDDEN_FROM_PUBLIC)
                        .stream()
                        .collect(Collectors.toMap(RatingAggregate::getReviewableId, Function.identity()));

        return packages.stream()
                .map(p -> toResponse(p, itineraries.getOrDefault(p.getId(), List.of()),
                        withDepartures.contains(p.getId()), ratings.get(p.getId())))
                .toList();
    }

    private TourPackageResponseDto toResponse(TourPackage tourPackage, List<ItineraryDayResponseDto> itineraryDays,
                                              boolean hasDepartures, RatingAggregate rating) {
        // Rounded the same way as ReviewServiceImpl#getRatingSummary.
        double averageRating = rating == null || rating.getAverageRating() == null
                ? 0.0
                : Math.round(rating.getAverageRating() * 10) / 10.0;
        long reviewCount = rating == null ? 0 : rating.getReviewCount();

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
                .rejectionReason(tourPackage.getRejectionReason())
                .itineraryDays(itineraryDays)
                .inclusions(tourPackage.getInclusions() == null ? new ArrayList<>() : new ArrayList<>(tourPackage.getInclusions()))
                .exclusions(tourPackage.getExclusions() == null ? new ArrayList<>() : new ArrayList<>(tourPackage.getExclusions()))
                .imageUrls(tourPackage.getImageUrls() == null ? new ArrayList<>() : new ArrayList<>(tourPackage.getImageUrls()))
                .hasDepartures(hasDepartures)
                .averageRating(averageRating)
                .reviewCount(reviewCount)
                .budgetTier(budgetTierPolicy.classify(tourPackage.getPrice(), tourPackage.getDurationDays()))
                .build();
    }

}
