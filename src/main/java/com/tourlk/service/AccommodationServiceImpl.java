package com.tourlk.service;

import com.tourlk.dto.AccommodationRequestDto;
import com.tourlk.dto.AccommodationResponseDto;
import com.tourlk.dto.DestinationResponseDto;
import com.tourlk.dto.RoomRequestDto;
import com.tourlk.dto.RoomResponseDto;
import com.tourlk.entity.Accommodation;
import com.tourlk.entity.Destination;
import com.tourlk.entity.Room;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AccommodationServiceImpl implements AccommodationService {

    /** Statuses shown in public browsing — TEMPORARILY_UNAVAILABLE stays visible, just not bookable. */
    private static final Set<AccommodationStatus> BROWSABLE = EnumSet.of(
            AccommodationStatus.ACTIVE, AccommodationStatus.FULLY_BOOKED, AccommodationStatus.TEMPORARILY_UNAVAILABLE);

    /** Statuses of a live listing (approved, not deactivated/archived). */
    private static final Set<AccommodationStatus> LIVE = BROWSABLE;

    private final AccommodationRepository accommodationRepository;
    private final RoomRepository roomRepository;
    private final RoomReservationRepository roomReservationRepository;
    private final DestinationService destinationService;
    private final NotificationService notificationService;

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
    public AccommodationResponseDto deactivateAccommodation(Long id, User currentUser) {
        Accommodation accommodation = getEntity(id);
        assertCanManage(accommodation, currentUser);
        assertLive(accommodation, "deactivated");

        accommodation.setStatus(AccommodationStatus.INACTIVE);
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

        accommodation.setStatus(AccommodationStatus.ACTIVE);
        return toResponse(accommodationRepository.save(accommodation));
    }

    @Override
    public AccommodationResponseDto archiveAccommodation(Long id, User currentUser) {
        Accommodation accommodation = getEntity(id);
        assertCanManage(accommodation, currentUser);

        if (accommodation.getStatus() == AccommodationStatus.ARCHIVED) {
            throw new InvalidStatusTransitionException("This accommodation is already archived");
        }

        accommodation.setStatus(AccommodationStatus.ARCHIVED);
        return toResponse(accommodationRepository.save(accommodation));
    }

    @Override
    public AccommodationResponseDto getById(Long id) {
        return toResponse(getEntity(id));
    }

    @Override
    public List<AccommodationResponseDto> getAllActive(Long locationId) {
        return accommodationRepository.search(BROWSABLE, locationId).stream()
                .map(this::toResponse)
                .toList();
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
    public RoomResponseDto updateRoom(Long roomId, RoomRequestDto request, User currentUser) {
        Room room = getRoomEntity(roomId);
        assertCanManage(room.getAccommodation(), currentUser);
        assertEditable(room.getAccommodation());

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
                room.getId(), RoomReservationStatus.CONFIRMED, today, today.plusDays(1)) < room.getTotalRooms());
    }

    private Accommodation getEntity(Long id) {
        return accommodationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Accommodation not found with id: " + id));
    }

    private Room getRoomEntity(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + id));
    }

    private void assertCanManage(Accommodation accommodation, User currentUser) {
        boolean isOwner = accommodation.getOwner().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isOwner && !isAdmin) {
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
                .facilities(accommodation.getFacilities() == null ? List.of() : List.copyOf(accommodation.getFacilities()))
                .policies(accommodation.getPolicies())
                .imageUrls(accommodation.getImageUrls() == null ? List.of() : List.copyOf(accommodation.getImageUrls()))
                .status(accommodation.getStatus())
                .ownerId(accommodation.getOwner().getId())
                .ownerName(accommodation.getOwner().getName())
                .rooms(rooms)
                .createdAt(accommodation.getCreatedAt())
                .build();
    }

}
