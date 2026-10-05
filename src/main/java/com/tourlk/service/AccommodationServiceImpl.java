package com.tourlk.service;

import com.tourlk.dto.AccommodationRequestDto;
import com.tourlk.dto.AccommodationResponseDto;
import com.tourlk.dto.DestinationResponseDto;
import com.tourlk.dto.RoomRequestDto;
import com.tourlk.dto.RoomResponseDto;
import com.tourlk.entity.Accommodation;
import com.tourlk.entity.Destination;
import com.tourlk.entity.Room;
import com.tourlk.entity.RoomReservation;
import com.tourlk.entity.User;
import com.tourlk.enums.AccommodationStatus;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.RoomReservationStatus;
import com.tourlk.enums.Role;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.AccommodationRepository;
import com.tourlk.repo.RoomRepository;
import com.tourlk.repo.RoomReservationRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class AccommodationServiceImpl implements AccommodationService {

    /** Statuses shown in public browsing — TEMPORARILY_UNAVAILABLE stays visible, just not bookable. */
    private static final Set<AccommodationStatus> BROWSABLE = EnumSet.of(
            AccommodationStatus.ACTIVE, AccommodationStatus.FULLY_BOOKED, AccommodationStatus.TEMPORARILY_UNAVAILABLE);

    private static final List<String> SORTS = List.of("name", "price_asc", "price_desc", "stars_desc");

    /** Statuses of a live listing (approved, not deactivated/archived). */
    private static final Set<AccommodationStatus> LIVE = BROWSABLE;

    private final AccommodationRepository accommodationRepository;
    private final RoomRepository roomRepository;
    private final RoomReservationRepository roomReservationRepository;
    private final DestinationService destinationService;
    private final NotificationService notificationService;
    private final RoomReservationService roomReservationService;

    // @Lazy: RoomReservationServiceImpl depends on AccommodationService, so this would be a cycle.
    public AccommodationServiceImpl(AccommodationRepository accommodationRepository,
                                    RoomRepository roomRepository,
                                    RoomReservationRepository roomReservationRepository,
                                    DestinationService destinationService,
                                    NotificationService notificationService,
                                    @Lazy RoomReservationService roomReservationService) {
        this.accommodationRepository = accommodationRepository;
        this.roomRepository = roomRepository;
        this.roomReservationRepository = roomReservationRepository;
        this.destinationService = destinationService;
        this.notificationService = notificationService;
        this.roomReservationService = roomReservationService;
    }

    @Override
    public AccommodationResponseDto createAccommodation(AccommodationRequestDto request, User currentUser) {
        Destination location = destinationService.requireSelectableDestination(request.getLocationId());

        Accommodation accommodation = Accommodation.builder()
                .name(request.getName())
                .description(request.getDescription())
                .location(location)
                .starRating(request.getStarRating())
                .status(AccommodationStatus.DRAFT)
                .owner(currentUser)
                .build();
        applyDetails(accommodation, request);

        return toResponse(accommodationRepository.save(accommodation));
    }

    @Override
    public AccommodationResponseDto updateAccommodation(Long id, AccommodationRequestDto request, User currentUser) {
        Accommodation accommodation = getEntity(id);
        assertCanManage(accommodation, currentUser);
        assertEditable(accommodation);

        accommodation.setName(request.getName());
        accommodation.setDescription(request.getDescription());
        accommodation.setLocation(destinationService.requireSelectableDestination(request.getLocationId()));
        accommodation.setStarRating(request.getStarRating());
        applyDetails(accommodation, request);

        return toResponse(accommodationRepository.save(accommodation));
    }

    @Override
    public AccommodationResponseDto submitForApproval(Long id, User currentUser) {
        Accommodation accommodation = getEntity(id);
        assertCanManage(accommodation, currentUser);
        assertStatus(accommodation, AccommodationStatus.DRAFT, "submitted for approval");

        accommodation.setStatus(AccommodationStatus.PENDING_APPROVAL);
        Accommodation saved = accommodationRepository.save(accommodation);
        notificationService.notifyAdmins(NotificationType.ACCOMMODATION_SUBMITTED, "Accommodation awaiting approval",
                "\"" + saved.getName() + "\" was submitted for approval", "/admin/accommodations");
        return toResponse(saved);
    }

    @Override
    public AccommodationResponseDto approveAccommodation(Long id) {
        Accommodation accommodation = getEntity(id);
        assertStatus(accommodation, AccommodationStatus.PENDING_APPROVAL, "approved");

        accommodation.setStatus(AccommodationStatus.ACTIVE);
        Accommodation saved = accommodationRepository.save(accommodation);
        notificationService.notify(saved.getOwner(), NotificationType.ACCOMMODATION_APPROVED, "Accommodation approved",
                "\"" + saved.getName() + "\" was approved and is now live.", "/accommodations/mine");
        return toResponse(saved);
    }

    @Override
    public AccommodationResponseDto rejectAccommodation(Long id) {
        Accommodation accommodation = getEntity(id);
        assertStatus(accommodation, AccommodationStatus.PENDING_APPROVAL, "rejected");

        accommodation.setStatus(AccommodationStatus.DRAFT);
        Accommodation saved = accommodationRepository.save(accommodation);
        notificationService.notify(saved.getOwner(), NotificationType.ACCOMMODATION_REJECTED, "Accommodation not approved",
                "\"" + saved.getName() + "\" was not approved. Update the listing and submit it again.",
                "/accommodations/mine");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public AccommodationResponseDto deactivateAccommodation(Long id, User currentUser) {
        Accommodation accommodation = getEntity(id);
        assertCanManage(accommodation, currentUser);
        assertLive(accommodation, "deactivated");

        boolean byAdmin = currentUser.getRole() == Role.ADMIN;
        releaseReservations(accommodation, byAdmin, "deactivated");

        accommodation.setStatus(AccommodationStatus.INACTIVE);
        accommodation.setDeactivatedByAdmin(byAdmin);
        return toResponse(accommodationRepository.save(accommodation));
    }

    @Override
    @Transactional
    public AccommodationResponseDto markTemporarilyUnavailable(Long id, User currentUser) {
        Accommodation accommodation = getEntity(id);
        assertCanManage(accommodation, currentUser);
        if (accommodation.getStatus() != AccommodationStatus.ACTIVE
                && accommodation.getStatus() != AccommodationStatus.FULLY_BOOKED) {
            throw new InvalidStatusTransitionException(
                    "Only ACTIVE or FULLY_BOOKED accommodations can be made temporarily unavailable, but this "
                            + "accommodation is " + accommodation.getStatus());
        }

        accommodation.setStatus(AccommodationStatus.TEMPORARILY_UNAVAILABLE);
        return toResponse(accommodationRepository.save(accommodation));
    }

    @Override
    @Transactional
    public AccommodationResponseDto resumeAvailability(Long id, User currentUser) {
        Accommodation accommodation = getEntity(id);
        assertCanManage(accommodation, currentUser);
        assertStatus(accommodation, AccommodationStatus.TEMPORARILY_UNAVAILABLE, "resumed");

        accommodation.setStatus(isFullyBookedToday(accommodation.getId())
                ? AccommodationStatus.FULLY_BOOKED : AccommodationStatus.ACTIVE);
        return toResponse(accommodationRepository.save(accommodation));
    }

    @Override
    public AccommodationResponseDto reactivateAccommodation(Long id, User currentUser) {
        Accommodation accommodation = getEntity(id);
        assertCanManage(accommodation, currentUser);
        assertStatus(accommodation, AccommodationStatus.INACTIVE, "reactivated");
        if (currentUser.getRole() != Role.ADMIN && Boolean.TRUE.equals(accommodation.getDeactivatedByAdmin())) {
            throw new AccessDeniedException(
                    "This accommodation was deactivated by an administrator; only an administrator can reactivate it");
        }

        accommodation.setStatus(AccommodationStatus.ACTIVE);
        accommodation.setDeactivatedByAdmin(false);
        return toResponse(accommodationRepository.save(accommodation));
    }

    @Override
    @Transactional
    public AccommodationResponseDto archiveAccommodation(Long id, User currentUser) {
        Accommodation accommodation = getEntity(id);
        assertCanManage(accommodation, currentUser);

        if (accommodation.getStatus() == AccommodationStatus.ARCHIVED) {
            throw new InvalidStatusTransitionException("This accommodation is already archived");
        }
        if (accommodation.getStatus() == AccommodationStatus.INACTIVE
                && Boolean.TRUE.equals(accommodation.getDeactivatedByAdmin())
                && currentUser.getRole() != Role.ADMIN) {
            throw new AccessDeniedException(
                    "This accommodation was deactivated by an administrator; only an administrator can archive it");
        }

        releaseReservations(accommodation, currentUser.getRole() == Role.ADMIN, "archived");

        accommodation.setStatus(AccommodationStatus.ARCHIVED);
        return toResponse(accommodationRepository.save(accommodation));
    }

    @Override
    public AccommodationResponseDto getById(Long id, User currentUser) {
        Accommodation accommodation = getEntity(id);
        // Drafts, pending, inactive and archived listings are only visible to whoever manages them;
        // everyone else gets the same 404 as for an id that doesn't exist.
        if (!BROWSABLE.contains(accommodation.getStatus()) && !canManage(accommodation, currentUser)) {
            throw new ResourceNotFoundException("Accommodation not found with id: " + id);
        }
        return toResponse(accommodation);
    }

    @Override
    public List<AccommodationResponseDto> getAllActive(Long locationId, String q, Integer minStars,
                                                       BigDecimal minPrice, BigDecimal maxPrice, String sort) {
        if (minStars != null && (minStars < 1 || minStars > 5)) {
            throw new BadRequestException("minStars must be between 1 and 5");
        }
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)) {
            throw new BadRequestException("Prices must not be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("minPrice must not be greater than maxPrice");
        }
        String sortKey = sort == null || sort.isBlank() ? null : sort.trim();
        if (sortKey != null && !SORTS.contains(sortKey)) {
            throw new BadRequestException("Unknown sort '" + sortKey + "'; use one of " + String.join(", ", SORTS));
        }

        List<AccommodationResponseDto> results = accommodationRepository
                .search(BROWSABLE, locationId, likePattern(q), minStars, minPrice, maxPrice).stream()
                .map(this::toResponse)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (sortKey != null) {
            results.sort(comparatorFor(sortKey));
        }
        return results;
    }

    /** "%text%" in lower case with LIKE wildcards escaped by '!', or null when there is nothing to search for. */
    private static String likePattern(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String escaped = q.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_").replace("[", "![");
        return "%" + escaped + "%";
    }

    private static Comparator<AccommodationResponseDto> comparatorFor(String sort) {
        Comparator<AccommodationResponseDto> byName =
                Comparator.comparing(AccommodationResponseDto::getName, String.CASE_INSENSITIVE_ORDER);
        // Cheapest room price; listings without rooms sort last in both directions.
        Comparator<AccommodationResponseDto> asc = Comparator.comparing(
                AccommodationServiceImpl::cheapestRoom, Comparator.nullsLast(Comparator.<BigDecimal>naturalOrder()));
        Comparator<AccommodationResponseDto> desc = Comparator.comparing(
                AccommodationServiceImpl::cheapestRoom, Comparator.nullsLast(Comparator.<BigDecimal>reverseOrder()));
        return switch (sort) {
            case "name" -> byName;
            case "price_asc" -> asc.thenComparing(byName);
            case "price_desc" -> desc.thenComparing(byName);
            default -> Comparator.comparing(AccommodationResponseDto::getStarRating,
                    Comparator.nullsLast(Comparator.<Integer>reverseOrder())).thenComparing(byName); // stars_desc
        };
    }

    private static BigDecimal cheapestRoom(AccommodationResponseDto accommodation) {
        return accommodation.getRooms().stream()
                .map(RoomResponseDto::getPricePerNight)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    @Override
    public List<AccommodationResponseDto> getPendingApproval() {
        return accommodationRepository.findByStatus(AccommodationStatus.PENDING_APPROVAL).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<AccommodationResponseDto> getByOwner(Long ownerId) {
        return accommodationRepository.findByOwnerId(ownerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public RoomResponseDto addRoom(Long accommodationId, RoomRequestDto request, User currentUser) {
        Accommodation accommodation = getEntity(accommodationId);
        assertCanManage(accommodation, currentUser);
        assertEditable(accommodation);

        Room room = Room.builder()
                .accommodation(accommodation)
                .roomType(request.getRoomType())
                .pricePerNight(request.getPricePerNight())
                .totalRooms(request.getTotalRooms())
                .maxOccupancy(request.getMaxOccupancy())
                .facilities(copyOrEmpty(request.getFacilities()))
                .imageUrls(copyOrEmpty(request.getImageUrls()))
                .build();

        return toRoomResponse(roomRepository.save(room));
    }

    @Override
    @Transactional
    public RoomResponseDto updateRoom(Long roomId, RoomRequestDto request, User currentUser) {
        // Same row lock reservations take, so a reservation can't slip in while capacity is being cut.
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + roomId));
        assertCanManage(room.getAccommodation(), currentUser);
        assertEditable(room.getAccommodation());
        if (request.getTotalRooms() < room.getTotalRooms()) {
            assertNotBelowReserved(room, request.getTotalRooms());
        }

        room.setRoomType(request.getRoomType());
        room.setPricePerNight(request.getPricePerNight());
        room.setTotalRooms(request.getTotalRooms());
        room.setMaxOccupancy(request.getMaxOccupancy());
        room.setFacilities(copyOrEmpty(request.getFacilities()));
        room.setImageUrls(copyOrEmpty(request.getImageUrls()));

        return toRoomResponse(roomRepository.save(room));
    }

    @Override
    public void removeRoom(Long roomId, User currentUser) {
        Room room = getRoomEntity(roomId);
        assertCanManage(room.getAccommodation(), currentUser);
        assertEditable(room.getAccommodation());

        if (!roomReservationRepository.findByRoomId(roomId).isEmpty()) {
            throw new BadRequestException(
                    "This room type has reservations and cannot be removed. Archive the property instead.");
        }

        roomRepository.delete(room);
    }

    @Override
    @Transactional
    public void refreshAvailabilityStatus(Long accommodationId) {
        Accommodation accommodation = getEntity(accommodationId);
        syncAvailability(accommodation);
    }

    @Override
    @Transactional
    public void refreshAllAvailabilityStatuses() {
        List<Accommodation> candidates = new ArrayList<>(accommodationRepository.findByStatus(AccommodationStatus.ACTIVE));
        candidates.addAll(accommodationRepository.findByStatus(AccommodationStatus.FULLY_BOOKED));
        candidates.forEach(this::syncAvailability);
    }

    /** ACTIVE <-> FULLY_BOOKED only; every other status (incl. TEMPORARILY_UNAVAILABLE) is left alone. */
    private void syncAvailability(Accommodation accommodation) {
        AccommodationStatus current = accommodation.getStatus();
        if (current != AccommodationStatus.ACTIVE && current != AccommodationStatus.FULLY_BOOKED) {
            return;
        }

        AccommodationStatus target = isFullyBookedToday(accommodation.getId())
                ? AccommodationStatus.FULLY_BOOKED : AccommodationStatus.ACTIVE;
        if (target != current) {
            accommodation.setStatus(target);
            accommodationRepository.save(accommodation);
        }
    }

    /**
     * True when the property has room types and none has a free unit tonight
     * (confirmed reservations overlapping [today, today + 1) use up all
     * {@code totalRooms}). A property with no room types is never "fully booked".
     */
    private boolean isFullyBookedToday(Long accommodationId) {
        List<Room> rooms = roomRepository.findByAccommodationId(accommodationId);
        if (rooms.isEmpty()) {
            return false;
        }

        LocalDate today = LocalDate.now();
        return rooms.stream().noneMatch(room -> roomReservationRepository.sumReservedRoomsOverlapping(
                room.getId(), java.util.EnumSet.of(RoomReservationStatus.CONFIRMED), today, today.plusDays(1), 0L) < room.getTotalRooms());
    }

    private Accommodation getEntity(Long id) {
        return accommodationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Accommodation not found with id: " + id));
    }

    private Room getRoomEntity(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + id));
    }

    /**
     * Peak occupancy of a room type always falls on some held stay's check-in night (or today, for a
     * stay already under way), so it is enough to sum the overlap on each such night.
     */
    private void assertNotBelowReserved(Room room, int newTotalRooms) {
        Set<RoomReservationStatus> holding = EnumSet.of(RoomReservationStatus.PENDING, RoomReservationStatus.CONFIRMED);
        LocalDate today = LocalDate.now();
        for (RoomReservation held : roomReservationRepository
                .findByRoomIdAndStatusInAndCheckOutDateAfter(room.getId(), holding, today)) {
            LocalDate night = held.getCheckInDate().isAfter(today) ? held.getCheckInDate() : today;
            int reserved = roomReservationRepository.sumReservedRoomsOverlapping(
                    room.getId(), holding, night, night.plusDays(1), 0L);
            if (reserved > newTotalRooms) {
                throw new BadRequestException(reserved + " room(s) are already reserved for the night of " + night
                        + "; you can't reduce below that");
            }
        }
    }

    /**
     * Before a property is deactivated/archived: an owner is blocked while CONFIRMED upcoming stays exist;
     * an admin forces it through (those are cancelled and refunded in full). PENDING reservations are always
     * cancelled, refunded in full if somehow paid, and the tourists notified.
     */
    private void releaseReservations(Accommodation accommodation, boolean force, String action) {
        LocalDate today = LocalDate.now();
        if (!force) {
            long confirmed = roomReservationRepository.findByRoomAccommodationIdAndStatusIn(
                            accommodation.getId(), EnumSet.of(RoomReservationStatus.CONFIRMED)).stream()
                    .filter(r -> r.getCheckOutDate().isAfter(today))
                    .count();
            if (confirmed > 0) {
                throw new BadRequestException("This accommodation has " + confirmed
                        + " confirmed upcoming reservation(s). Complete or cancel them before it can be " + action + ".");
            }
        }
        Set<RoomReservationStatus> toCancel = force
                ? EnumSet.of(RoomReservationStatus.PENDING, RoomReservationStatus.CONFIRMED)
                : EnumSet.of(RoomReservationStatus.PENDING);
        roomReservationService.cancelForAccommodation(
                accommodation.getId(), toCancel, "the property was " + action + ".");
    }

    private boolean canManage(Accommodation accommodation, User user) {
        return user != null
                && (accommodation.getOwner().getId().equals(user.getId()) || user.getRole() == Role.ADMIN);
    }

    private void assertCanManage(Accommodation accommodation, User currentUser) {
        if (!canManage(accommodation, currentUser)) {
            throw new AccessDeniedException("You do not have permission to manage this accommodation");
        }
    }

    private void assertEditable(Accommodation accommodation) {
        if (accommodation.getStatus() != AccommodationStatus.DRAFT && !LIVE.contains(accommodation.getStatus())) {
            throw new InvalidStatusTransitionException(
                    "An accommodation (and its rooms) can only be edited while it is DRAFT or live (ACTIVE, "
                            + "FULLY_BOOKED, TEMPORARILY_UNAVAILABLE), not " + accommodation.getStatus());
        }
    }

    private void assertLive(Accommodation accommodation, String action) {
        if (!LIVE.contains(accommodation.getStatus())) {
            throw new InvalidStatusTransitionException(
                    "Only live (ACTIVE, FULLY_BOOKED, TEMPORARILY_UNAVAILABLE) accommodations can be " + action
                            + ", but this accommodation is " + accommodation.getStatus());
        }
    }

    private void applyDetails(Accommodation accommodation, AccommodationRequestDto request) {
        if ((request.getLatitude() == null) != (request.getLongitude() == null)) {
            throw new BadRequestException("Latitude and longitude must be provided together");
        }
        accommodation.setLatitude(request.getLatitude());
        accommodation.setLongitude(request.getLongitude());
        accommodation.setAddress(request.getAddress() == null || request.getAddress().isBlank()
                ? null : request.getAddress().trim());
        accommodation.setFacilities(copyOrEmpty(request.getFacilities()));
        accommodation.setPolicies(request.getPolicies() == null || request.getPolicies().isBlank()
                ? null : request.getPolicies());
        accommodation.setImageUrls(copyOrEmpty(request.getImageUrls()));
    }

    private List<String> copyOrEmpty(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private void assertStatus(Accommodation accommodation, AccommodationStatus required, String action) {
        if (accommodation.getStatus() != required) {
            throw new InvalidStatusTransitionException(
                    "Only " + required + " accommodations can be " + action + ", but this accommodation is "
                            + accommodation.getStatus());
        }
    }

    /**
     * Lightweight destination view embedded in an accommodation response
     * — id + name + region + status, no active-listing counts.
     */
    private DestinationResponseDto toDestinationSummary(Destination destination) {
        return DestinationResponseDto.builder()
                .id(destination.getId())
                .name(destination.getName())
                .region(destination.resolveRegion())
                .province(destination.resolveProvince())
                .district(destination.getDistrict())
                .status(destination.getStatus())
                .latitude(destination.getLatitude())
                .longitude(destination.getLongitude())
                .build();
    }

    private RoomResponseDto toRoomResponse(Room room) {
        return RoomResponseDto.builder()
                .id(room.getId())
                .accommodationId(room.getAccommodation().getId())
                .roomType(room.getRoomType())
                .pricePerNight(room.getPricePerNight())
                .totalRooms(room.getTotalRooms())
                .maxOccupancy(room.getMaxOccupancy())
                .facilities(room.getFacilities() == null ? List.of() : List.copyOf(room.getFacilities()))
                .imageUrls(room.getImageUrls() == null ? List.of() : List.copyOf(room.getImageUrls()))
                .build();
    }

    private AccommodationResponseDto toResponse(Accommodation accommodation) {
        List<RoomResponseDto> rooms = roomRepository.findByAccommodationId(accommodation.getId()).stream()
                .map(this::toRoomResponse)
                .toList();

        return AccommodationResponseDto.builder()
                .id(accommodation.getId())
                .name(accommodation.getName())
                .description(accommodation.getDescription())
                .location(toDestinationSummary(accommodation.getLocation()))
                .starRating(accommodation.getStarRating())
                .address(accommodation.getAddress())
                .latitude(accommodation.getLatitude())
                .longitude(accommodation.getLongitude())
                .facilities(accommodation.getFacilities() == null ? List.of() : List.copyOf(accommodation.getFacilities()))
                .policies(accommodation.getPolicies())
                .imageUrls(accommodation.getImageUrls() == null ? List.of() : List.copyOf(accommodation.getImageUrls()))
                .status(accommodation.getStatus())
                .deactivatedByAdmin(Boolean.TRUE.equals(accommodation.getDeactivatedByAdmin()))
                .ownerId(accommodation.getOwner().getId())
                .ownerName(accommodation.getOwner().getName())
                .rooms(rooms)
                .createdAt(accommodation.getCreatedAt())
                .build();
    }

}
