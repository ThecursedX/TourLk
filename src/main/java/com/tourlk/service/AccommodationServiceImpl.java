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

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccommodationServiceImpl implements AccommodationService {

    private final AccommodationRepository accommodationRepository;
    private final RoomRepository roomRepository;
    private final RoomReservationRepository roomReservationRepository;
    private final DestinationService destinationService;

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

        return toResponse(accommodationRepository.save(accommodation));
    }

    @Override
    public AccommodationResponseDto submitForApproval(Long id, User currentUser) {
        Accommodation accommodation = getEntity(id);
        assertCanManage(accommodation, currentUser);
        assertStatus(accommodation, AccommodationStatus.DRAFT, "submitted for approval");

        accommodation.setStatus(AccommodationStatus.PENDING_APPROVAL);
        return toResponse(accommodationRepository.save(accommodation));
    }

    @Override
    public AccommodationResponseDto approveAccommodation(Long id) {
        Accommodation accommodation = getEntity(id);
        assertStatus(accommodation, AccommodationStatus.PENDING_APPROVAL, "approved");

        accommodation.setStatus(AccommodationStatus.ACTIVE);
        return toResponse(accommodationRepository.save(accommodation));
    }

    @Override
    public AccommodationResponseDto rejectAccommodation(Long id) {
        Accommodation accommodation = getEntity(id);
        assertStatus(accommodation, AccommodationStatus.PENDING_APPROVAL, "rejected");

        accommodation.setStatus(AccommodationStatus.DRAFT);
        return toResponse(accommodationRepository.save(accommodation));
    }

    @Override
    public AccommodationResponseDto deactivateAccommodation(Long id, User currentUser) {
        Accommodation accommodation = getEntity(id);
        assertCanManage(accommodation, currentUser);
        assertStatus(accommodation, AccommodationStatus.ACTIVE, "deactivated");

        accommodation.setStatus(AccommodationStatus.INACTIVE);
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
        return accommodationRepository.search(AccommodationStatus.ACTIVE, locationId).stream()
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
        if (accommodation.getStatus() != AccommodationStatus.DRAFT
                && accommodation.getStatus() != AccommodationStatus.ACTIVE) {
            throw new InvalidStatusTransitionException(
                    "An accommodation (and its rooms) can only be edited while it is DRAFT or ACTIVE, not "
                            + accommodation.getStatus());
        }
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
                .region(destination.getRegion())
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
                .status(accommodation.getStatus())
                .ownerId(accommodation.getOwner().getId())
                .ownerName(accommodation.getOwner().getName())
                .rooms(rooms)
                .createdAt(accommodation.getCreatedAt())
                .build();
    }

}
