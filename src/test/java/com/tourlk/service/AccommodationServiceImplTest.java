package com.tourlk.service;

import com.tourlk.dto.AccommodationRequestDto;
import com.tourlk.dto.AccommodationResponseDto;
import com.tourlk.dto.RoomRequestDto;
import com.tourlk.dto.RoomResponseDto;
import com.tourlk.entity.Accommodation;
import com.tourlk.entity.Destination;
import com.tourlk.entity.Room;
import com.tourlk.entity.RoomReservation;
import com.tourlk.entity.User;
import com.tourlk.enums.AccommodationStatus;
import com.tourlk.enums.DestinationStatus;
import com.tourlk.enums.Role;
import com.tourlk.enums.RoomReservationStatus;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.DestinationInactiveException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.AccommodationRepository;
import com.tourlk.repo.RoomRepository;
import com.tourlk.repo.RoomReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AccommodationServiceImpl}: the approval workflow,
 * the owner/admin guard, the "editable only while DRAFT/ACTIVE" rule, and
 * room-type add/update/remove (incl. the "has reservations" removal block).
 */
@ExtendWith(MockitoExtension.class)
class AccommodationServiceImplTest {

    @Mock
    private AccommodationRepository accommodationRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private RoomReservationRepository roomReservationRepository;
    @Mock
    private DestinationService destinationService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private RoomReservationService roomReservationService;

    @InjectMocks
    private AccommodationServiceImpl service;

    private User owner;
    private User stranger;
    private User admin;
    private Destination galle;
    private Destination mirissa;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).name("Holly Host").email("holly@example.com").role(Role.HOTEL_PARTNER).build();
        stranger = User.builder().id(2L).name("Other Host").role(Role.HOTEL_PARTNER).build();
        admin = User.builder().id(9L).name("Amy Admin").role(Role.ADMIN).build();
        galle = Destination.builder()
                .id(1L).name("Galle").region("Southern Province").status(DestinationStatus.PUBLISHED).build();
        mirissa = Destination.builder()
                .id(2L).name("Mirissa").region("Southern Province").status(DestinationStatus.PUBLISHED).build();
        // toResponse() always loads the room list for the accommodation.
        lenient().when(roomRepository.findByAccommodationId(any())).thenReturn(List.of());
        // create/update resolve the location by id; not every test hits that path.
        lenient().when(destinationService.requireSelectableDestination(1L)).thenReturn(galle);
        lenient().when(destinationService.requireSelectableDestination(2L)).thenReturn(mirissa);
    }

    private Accommodation accommodation(AccommodationStatus status) {
        return Accommodation.builder()
                .id(30L).name("Ocean View").description("Beachfront").location(galle)
                .starRating(4).status(status).owner(owner)
                .build();
    }

    private Room room(AccommodationStatus accStatus) {
        return Room.builder()
                .id(40L).accommodation(accommodation(accStatus)).roomType("Deluxe")
                .pricePerNight(new BigDecimal("80.00")).totalRooms(5).maxOccupancy(2)
                .build();
    }

    private AccommodationRequestDto accRequest() {
        return accRequest("Ocean View", "Beachfront", 1L, 4);
    }

    private AccommodationRequestDto accRequest(String name, String description, Long locationId, Integer stars) {
        AccommodationRequestDto dto = new AccommodationRequestDto();
        dto.setName(name);
        dto.setDescription(description);
        dto.setLocationId(locationId);
        dto.setStarRating(stars);
        return dto;
    }

    private RoomRequestDto roomRequest() {
        RoomRequestDto dto = new RoomRequestDto();
        dto.setRoomType("Suite");
        dto.setPricePerNight(new BigDecimal("120.00"));
        dto.setTotalRooms(3);
        dto.setMaxOccupancy(4);
        return dto;
    }

    private void expectAccSaveEchoed() {
        when(accommodationRepository.save(any(Accommodation.class))).thenAnswer(inv -> {
            Accommodation a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(30L);
            }
            return a;
        });
    }

    @Nested
    class CreateAndUpdate {

        @Test
        void createAccommodation_withCoordinates_savesAndReturnsThem() {
            expectAccSaveEchoed();
            AccommodationRequestDto request = accRequest();
            request.setLatitude(6.9271);
            request.setLongitude(79.8612);

            AccommodationResponseDto result = service.createAccommodation(request, owner);

            assertThat(result.getLatitude()).isEqualTo(6.9271);
            assertThat(result.getLongitude()).isEqualTo(79.8612);
        }

        @Test
        void createAccommodation_withoutCoordinates_leavesThemNull() {
            expectAccSaveEchoed();

            AccommodationResponseDto result = service.createAccommodation(accRequest(), owner);

            assertThat(result.getLatitude()).isNull();
            assertThat(result.getLongitude()).isNull();
        }

        @Test
        void createAccommodation_onlyOneCoordinate_isRejected() {
            AccommodationRequestDto request = accRequest();
            request.setLatitude(6.9271);

            assertThatThrownBy(() -> service.createAccommodation(request, owner))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Latitude and longitude must be provided together");
        }

        @Test
        void createAccommodation_savesAsDraftOwnedByCurrentUser() {
            expectAccSaveEchoed();

            AccommodationResponseDto result = service.createAccommodation(accRequest(), owner);

            assertThat(result.getStatus()).isEqualTo(AccommodationStatus.DRAFT);
            assertThat(result.getOwnerId()).isEqualTo(1L);
        }

        @Test
        void createAccommodation_inactiveLocation_propagatesDestinationInactive() {
            when(destinationService.requireSelectableDestination(1L))
                    .thenThrow(new DestinationInactiveException("Destination 'Galle' is inactive"));

            assertThatThrownBy(() -> service.createAccommodation(accRequest(), owner))
                    .isInstanceOf(DestinationInactiveException.class);
            verify(accommodationRepository, never()).save(any());
        }

        @Test
        void updateAccommodation_draftByOwner_appliesChanges() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.DRAFT)));
            expectAccSaveEchoed();

            AccommodationRequestDto edit = accRequest("Renamed", "New desc", 2L, 5);
            AccommodationResponseDto result = service.updateAccommodation(30L, edit, owner);

            assertThat(result.getName()).isEqualTo("Renamed");
            assertThat(result.getLocation().getName()).isEqualTo("Mirissa");
        }

        @Test
        void updateAccommodation_byUnrelatedHost_throwsAccessDenied() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.DRAFT)));

            assertThatThrownBy(() -> service.updateAccommodation(30L, accRequest(), stranger))
                    .isInstanceOf(AccessDeniedException.class);
            verify(accommodationRepository, never()).save(any());
        }

        @Test
        void updateAccommodation_whileArchived_throwsInvalidStatusTransition() {
            when(accommodationRepository.findById(30L))
                    .thenReturn(Optional.of(accommodation(AccommodationStatus.ARCHIVED)));

            assertThatThrownBy(() -> service.updateAccommodation(30L, accRequest(), owner))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void getById_notFound_throwsResourceNotFound() {
            when(accommodationRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(404L, null)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class ApprovalWorkflow {

        @Test
        void submitForApproval_draftByOwner_movesToPendingApproval() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.DRAFT)));
            expectAccSaveEchoed();

            assertThat(service.submitForApproval(30L, owner).getStatus())
                    .isEqualTo(AccommodationStatus.PENDING_APPROVAL);
        }

        @Test
        void approveAccommodation_pendingApproval_becomesActive() {
            when(accommodationRepository.findById(30L))
                    .thenReturn(Optional.of(accommodation(AccommodationStatus.PENDING_APPROVAL)));
            expectAccSaveEchoed();

            assertThat(service.approveAccommodation(30L).getStatus()).isEqualTo(AccommodationStatus.ACTIVE);
        }

        @Test
        void rejectAccommodation_pendingApproval_revertsToDraft() {
            when(accommodationRepository.findById(30L))
                    .thenReturn(Optional.of(accommodation(AccommodationStatus.PENDING_APPROVAL)));
            expectAccSaveEchoed();

            assertThat(service.rejectAccommodation(30L).getStatus()).isEqualTo(AccommodationStatus.DRAFT);
        }

        @Test
        void deactivateAccommodation_active_becomesInactive() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.ACTIVE)));
            expectAccSaveEchoed();

            assertThat(service.deactivateAccommodation(30L, owner).getStatus())
                    .isEqualTo(AccommodationStatus.INACTIVE);
        }

        @Test
        void archiveAccommodation_alreadyArchived_throwsInvalidStatusTransition() {
            when(accommodationRepository.findById(30L))
                    .thenReturn(Optional.of(accommodation(AccommodationStatus.ARCHIVED)));

            assertThatThrownBy(() -> service.archiveAccommodation(30L, owner))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }

    @Nested
    class ExtendedFieldsAndAvailability {

        @Test
        void createAccommodation_persistsAndReturnsExtendedFields() {
            expectAccSaveEchoed();
            AccommodationRequestDto dto = accRequest();
            dto.setAddress("  12 Beach Rd, Galle ");
            dto.setFacilities(List.of("Pool", "WiFi"));
            dto.setPolicies("Check-in after 2pm");
            dto.setImageUrls(List.of("http://img/1.jpg"));

            AccommodationResponseDto result = service.createAccommodation(dto, owner);

            assertThat(result.getAddress()).isEqualTo("12 Beach Rd, Galle");
            assertThat(result.getFacilities()).containsExactly("Pool", "WiFi");
            assertThat(result.getPolicies()).isEqualTo("Check-in after 2pm");
            assertThat(result.getImageUrls()).containsExactly("http://img/1.jpg");
        }

        @Test
        void addRoom_persistsFacilitiesAndImages() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.ACTIVE)));
            when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));
            RoomRequestDto dto = roomRequest();
            dto.setFacilities(List.of("Balcony"));
            dto.setImageUrls(List.of("http://img/r1.jpg"));

            RoomResponseDto result = service.addRoom(30L, dto, owner);

            assertThat(result.getFacilities()).containsExactly("Balcony");
            assertThat(result.getImageUrls()).containsExactly("http://img/r1.jpg");
        }

        @Test
        void markTemporarilyUnavailable_active_pausesBookings() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.ACTIVE)));
            expectAccSaveEchoed();

            assertThat(service.markTemporarilyUnavailable(30L, owner).getStatus())
                    .isEqualTo(AccommodationStatus.TEMPORARILY_UNAVAILABLE);
        }

        @Test
        void markTemporarilyUnavailable_byUnrelatedHost_throwsAccessDenied() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.ACTIVE)));

            assertThatThrownBy(() -> service.markTemporarilyUnavailable(30L, stranger))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void markTemporarilyUnavailable_draft_throwsInvalidStatusTransition() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.DRAFT)));

            assertThatThrownBy(() -> service.markTemporarilyUnavailable(30L, owner))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void resumeAvailability_withFreeRooms_becomesActive() {
            when(accommodationRepository.findById(30L))
                    .thenReturn(Optional.of(accommodation(AccommodationStatus.TEMPORARILY_UNAVAILABLE)));
            when(roomRepository.findByAccommodationId(30L)).thenReturn(List.of(room(AccommodationStatus.ACTIVE)));
            when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(1);
            expectAccSaveEchoed();

            assertThat(service.resumeAvailability(30L, owner).getStatus()).isEqualTo(AccommodationStatus.ACTIVE);
        }

        @Test
        void resumeAvailability_whenEverythingBookedTonight_becomesFullyBooked() {
            when(accommodationRepository.findById(30L))
                    .thenReturn(Optional.of(accommodation(AccommodationStatus.TEMPORARILY_UNAVAILABLE)));
            when(roomRepository.findByAccommodationId(30L)).thenReturn(List.of(room(AccommodationStatus.ACTIVE)));
            when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(5);
            expectAccSaveEchoed();

            assertThat(service.resumeAvailability(30L, owner).getStatus())
                    .isEqualTo(AccommodationStatus.FULLY_BOOKED);
        }

        @Test
        void resumeAvailability_notPaused_throwsInvalidStatusTransition() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.ACTIVE)));

            assertThatThrownBy(() -> service.resumeAvailability(30L, owner))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void refreshAvailabilityStatus_everyRoomTypeFullToday_setsFullyBooked() {
            Accommodation acc = accommodation(AccommodationStatus.ACTIVE);
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(acc));
            when(roomRepository.findByAccommodationId(30L))
                    .thenReturn(List.of(room(AccommodationStatus.ACTIVE), room(AccommodationStatus.ACTIVE)));
            when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(5);

            service.refreshAvailabilityStatus(30L);

            assertThat(acc.getStatus()).isEqualTo(AccommodationStatus.FULLY_BOOKED);
            verify(accommodationRepository).save(acc);
        }

        @Test
        void refreshAvailabilityStatus_oneRoomTypeStillFree_staysActive() {
            Accommodation acc = accommodation(AccommodationStatus.ACTIVE);
            Room full = room(AccommodationStatus.ACTIVE);
            Room free = room(AccommodationStatus.ACTIVE);
            free.setId(41L);
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(acc));
            when(roomRepository.findByAccommodationId(30L)).thenReturn(List.of(full, free));
            when(roomReservationRepository.sumReservedRoomsOverlapping(eq(40L), any(), any(), any(), any())).thenReturn(5);
            when(roomReservationRepository.sumReservedRoomsOverlapping(eq(41L), any(), any(), any(), any())).thenReturn(4);

            service.refreshAvailabilityStatus(30L);

            assertThat(acc.getStatus()).isEqualTo(AccommodationStatus.ACTIVE);
            verify(accommodationRepository, never()).save(any());
        }

        @Test
        void refreshAvailabilityStatus_fullyBookedThatFreedUp_reopens() {
            Accommodation acc = accommodation(AccommodationStatus.FULLY_BOOKED);
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(acc));
            when(roomRepository.findByAccommodationId(30L)).thenReturn(List.of(room(AccommodationStatus.ACTIVE)));
            when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(4);

            service.refreshAvailabilityStatus(30L);

            assertThat(acc.getStatus()).isEqualTo(AccommodationStatus.ACTIVE);
        }

        @Test
        void refreshAvailabilityStatus_noRoomTypes_isNeverFullyBooked() {
            Accommodation acc = accommodation(AccommodationStatus.ACTIVE);
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(acc));
            when(roomRepository.findByAccommodationId(30L)).thenReturn(List.of());

            service.refreshAvailabilityStatus(30L);

            assertThat(acc.getStatus()).isEqualTo(AccommodationStatus.ACTIVE);
        }

        @Test
        void refreshAvailabilityStatus_temporarilyUnavailable_isLeftAlone() {
            Accommodation acc = accommodation(AccommodationStatus.TEMPORARILY_UNAVAILABLE);
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(acc));

            service.refreshAvailabilityStatus(30L);

            assertThat(acc.getStatus()).isEqualTo(AccommodationStatus.TEMPORARILY_UNAVAILABLE);
            verify(accommodationRepository, never()).save(any());
        }

        @Test
        void refreshAllAvailabilityStatuses_checksActiveAndFullyBookedOnly() {
            Accommodation active = accommodation(AccommodationStatus.ACTIVE);
            Accommodation full = accommodation(AccommodationStatus.FULLY_BOOKED);
            when(accommodationRepository.findByStatus(AccommodationStatus.ACTIVE)).thenReturn(List.of(active));
            when(accommodationRepository.findByStatus(AccommodationStatus.FULLY_BOOKED)).thenReturn(List.of(full));
            when(roomRepository.findByAccommodationId(30L)).thenReturn(List.of(room(AccommodationStatus.ACTIVE)));
            when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(5);

            service.refreshAllAvailabilityStatuses();

            assertThat(active.getStatus()).isEqualTo(AccommodationStatus.FULLY_BOOKED);
            assertThat(full.getStatus()).isEqualTo(AccommodationStatus.FULLY_BOOKED);
        }

        @Test
        void getAllActive_includesFullyBookedAndTemporarilyUnavailableListings() {
            when(accommodationRepository.search(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

            service.getAllActive(null, null, null, null, null, null);

            org.mockito.ArgumentCaptor<java.util.Collection<AccommodationStatus>> statuses =
                    org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
            verify(accommodationRepository).search(statuses.capture(), any(), any(), any(), any(), any());
            assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                    AccommodationStatus.ACTIVE, AccommodationStatus.FULLY_BOOKED,
                    AccommodationStatus.TEMPORARILY_UNAVAILABLE);
        }

        @Test
        void updateAccommodation_whileTemporarilyUnavailable_isAllowed() {
            when(accommodationRepository.findById(30L))
                    .thenReturn(Optional.of(accommodation(AccommodationStatus.TEMPORARILY_UNAVAILABLE)));
            expectAccSaveEchoed();

            assertThat(service.updateAccommodation(30L, accRequest(), owner).getStatus())
                    .isEqualTo(AccommodationStatus.TEMPORARILY_UNAVAILABLE);
        }
    }

    @Nested
    class RoomManagement {

        @Test
        void addRoom_editableAccommodationByOwner_savesRoom() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.ACTIVE)));
            when(roomRepository.save(any(Room.class))).thenAnswer(inv -> {
                Room r = inv.getArgument(0);
                r.setId(40L);
                return r;
            });

            RoomResponseDto result = service.addRoom(30L, roomRequest(), owner);

            assertThat(result.getRoomType()).isEqualTo("Suite");
            assertThat(result.getAccommodationId()).isEqualTo(30L);
        }

        @Test
        void addRoom_whenAccommodationArchived_throwsInvalidStatusTransition() {
            when(accommodationRepository.findById(30L))
                    .thenReturn(Optional.of(accommodation(AccommodationStatus.ARCHIVED)));

            assertThatThrownBy(() -> service.addRoom(30L, roomRequest(), owner))
                    .isInstanceOf(InvalidStatusTransitionException.class);
            verify(roomRepository, never()).save(any());
        }

        @Test
        void addRoom_byUnrelatedHost_throwsAccessDenied() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.ACTIVE)));

            assertThatThrownBy(() -> service.addRoom(30L, roomRequest(), stranger))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void updateRoom_byOwner_appliesChanges() {
            when(roomRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(room(AccommodationStatus.ACTIVE)));
            when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

            RoomResponseDto result = service.updateRoom(40L, roomRequest(), owner);

            assertThat(result.getRoomType()).isEqualTo("Suite");
            assertThat(result.getTotalRooms()).isEqualTo(3);
        }

        @Test
        void removeRoom_withNoReservations_deletesRoom() {
            Room room = room(AccommodationStatus.ACTIVE);
            when(roomRepository.findById(40L)).thenReturn(Optional.of(room));
            when(roomReservationRepository.findByRoomId(40L)).thenReturn(List.of());

            service.removeRoom(40L, owner);

            verify(roomRepository).delete(room);
        }

        @Test
        void removeRoom_withExistingReservations_throwsBadRequest() {
            when(roomRepository.findById(40L)).thenReturn(Optional.of(room(AccommodationStatus.ACTIVE)));
            when(roomReservationRepository.findByRoomId(40L)).thenReturn(List.of(new RoomReservation()));

            assertThatThrownBy(() -> service.removeRoom(40L, owner))
                    .isInstanceOf(BadRequestException.class);
            verify(roomRepository, never()).delete(any());
        }

        @Test
        void removeRoom_byUnrelatedHost_throwsAccessDenied() {
            when(roomRepository.findById(40L)).thenReturn(Optional.of(room(AccommodationStatus.ACTIVE)));

            assertThatThrownBy(() -> service.removeRoom(40L, stranger))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ------------------------------------------------------------------
    // visibility, capacity edits, deactivate/archive with reservations, admin deactivation
    // ------------------------------------------------------------------

    private RoomReservation stay(RoomReservationStatus status, int rooms, int startsInDays) {
        return RoomReservation.builder()
                .id(70L).room(room(AccommodationStatus.ACTIVE)).status(status).numberOfRooms(rooms)
                .checkInDate(LocalDate.now().plusDays(startsInDays))
                .checkOutDate(LocalDate.now().plusDays(startsInDays + 2L))
                .build();
    }

    private RoomRequestDto roomRequestWithTotal(int totalRooms) {
        RoomRequestDto dto = roomRequest();
        dto.setTotalRooms(totalRooms);
        return dto;
    }

    @Nested
    class Visibility {

        @Test
        void getById_hiddenStatusesAsAnonymous_throwResourceNotFound() {
            for (AccommodationStatus status : List.of(AccommodationStatus.DRAFT,
                    AccommodationStatus.PENDING_APPROVAL, AccommodationStatus.INACTIVE, AccommodationStatus.ARCHIVED)) {
                when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(status)));

                assertThatThrownBy(() -> service.getById(30L, null))
                        .as("status %s", status)
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }

        @Test
        void getById_hiddenListingAsUnrelatedUser_throwsResourceNotFound() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.DRAFT)));

            assertThatThrownBy(() -> service.getById(30L, stranger)).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getById_hiddenListingAsOwner_returnsIt() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.DRAFT)));

            assertThat(service.getById(30L, owner).getStatus()).isEqualTo(AccommodationStatus.DRAFT);
        }

        @Test
        void getById_hiddenListingAsAdmin_returnsIt() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.ARCHIVED)));

            assertThat(service.getById(30L, admin).getStatus()).isEqualTo(AccommodationStatus.ARCHIVED);
        }

        @Test
        void getById_browsableStatusesAsAnonymous_areReturned() {
            for (AccommodationStatus status : List.of(AccommodationStatus.ACTIVE,
                    AccommodationStatus.FULLY_BOOKED, AccommodationStatus.TEMPORARILY_UNAVAILABLE)) {
                when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(status)));

                assertThat(service.getById(30L, null).getStatus()).isEqualTo(status);
            }
        }
    }

    @Nested
    class RoomCapacityEdits {

        @Test
        void updateRoom_reducingBelowRoomsAlreadyHeld_throwsBadRequest() {
            when(roomRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(room(AccommodationStatus.ACTIVE)));
            RoomReservation held = stay(RoomReservationStatus.CONFIRMED, 4, 5);
            when(roomReservationRepository.findByRoomIdAndStatusInAndCheckOutDateAfter(eq(40L), any(), any()))
                    .thenReturn(List.of(held));
            when(roomReservationRepository.sumReservedRoomsOverlapping(
                    eq(40L), any(), eq(held.getCheckInDate()), eq(held.getCheckInDate().plusDays(1)), eq(0L)))
                    .thenReturn(4);

            assertThatThrownBy(() -> service.updateRoom(40L, roomRequestWithTotal(3), owner))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("4 room(s) are already reserved")
                    .hasMessageContaining(held.getCheckInDate().toString());
            verify(roomRepository, never()).save(any());
        }

        @Test
        void updateRoom_reducingToExactlyTheHeldPeak_isAllowed() {
            when(roomRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(room(AccommodationStatus.ACTIVE)));
            when(roomReservationRepository.findByRoomIdAndStatusInAndCheckOutDateAfter(eq(40L), any(), any()))
                    .thenReturn(List.of(stay(RoomReservationStatus.PENDING, 3, 5)));
            when(roomReservationRepository.sumReservedRoomsOverlapping(any(), any(), any(), any(), any())).thenReturn(3);
            when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.updateRoom(40L, roomRequestWithTotal(3), owner).getTotalRooms()).isEqualTo(3);
        }

        @Test
        void updateRoom_stayAlreadyUnderway_isCheckedAgainstTonight() {
            when(roomRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(room(AccommodationStatus.ACTIVE)));
            RoomReservation underway = stay(RoomReservationStatus.CONFIRMED, 4, -1);
            when(roomReservationRepository.findByRoomIdAndStatusInAndCheckOutDateAfter(eq(40L), any(), any()))
                    .thenReturn(List.of(underway));
            LocalDate today = LocalDate.now();
            when(roomReservationRepository.sumReservedRoomsOverlapping(
                    eq(40L), any(), eq(today), eq(today.plusDays(1)), eq(0L))).thenReturn(4);

            assertThatThrownBy(() -> service.updateRoom(40L, roomRequestWithTotal(2), owner))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void updateRoom_increasingTotalRooms_doesNotCheckReservations() {
            when(roomRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(room(AccommodationStatus.ACTIVE)));
            when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

            service.updateRoom(40L, roomRequestWithTotal(9), owner);

            verify(roomReservationRepository, never())
                    .findByRoomIdAndStatusInAndCheckOutDateAfter(any(), any(), any());
        }
    }

    @Nested
    class DeactivateAndArchiveWithReservations {

        private void stubConfirmedUpcoming(boolean present) {
            when(roomReservationRepository.findByRoomAccommodationIdAndStatusIn(eq(30L), any()))
                    .thenReturn(present ? List.of(stay(RoomReservationStatus.CONFIRMED, 1, 5)) : List.of());
        }

        @Test
        void deactivate_byOwnerWithConfirmedUpcomingStay_isBlocked() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.ACTIVE)));
            stubConfirmedUpcoming(true);

            assertThatThrownBy(() -> service.deactivateAccommodation(30L, owner))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Complete or cancel");
            verify(roomReservationService, never()).cancelForAccommodation(any(), any(), any());
            verify(accommodationRepository, never()).save(any());
        }

        @Test
        void deactivate_byOwnerWithOnlyPending_cancelsPendingAndClearsAdminFlag() {
            Accommodation acc = accommodation(AccommodationStatus.ACTIVE);
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(acc));
            stubConfirmedUpcoming(false);
            expectAccSaveEchoed();

            AccommodationResponseDto result = service.deactivateAccommodation(30L, owner);

            assertThat(result.getStatus()).isEqualTo(AccommodationStatus.INACTIVE);
            assertThat(result.getDeactivatedByAdmin()).isFalse();
            verify(roomReservationService).cancelForAccommodation(eq(30L),
                    eq(EnumSet.of(RoomReservationStatus.PENDING)), any());
        }

        @Test
        void deactivate_byAdminWithConfirmedUpcomingStay_forcesCancelAndFlagsAdmin() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.ACTIVE)));
            expectAccSaveEchoed();

            AccommodationResponseDto result = service.deactivateAccommodation(30L, admin);

            assertThat(result.getStatus()).isEqualTo(AccommodationStatus.INACTIVE);
            assertThat(result.getDeactivatedByAdmin()).isTrue();
            verify(roomReservationService).cancelForAccommodation(eq(30L),
                    eq(EnumSet.of(RoomReservationStatus.PENDING, RoomReservationStatus.CONFIRMED)), any());
        }

        @Test
        void archive_byOwnerWithConfirmedUpcomingStay_isBlocked() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.ACTIVE)));
            stubConfirmedUpcoming(true);

            assertThatThrownBy(() -> service.archiveAccommodation(30L, owner))
                    .isInstanceOf(BadRequestException.class);
            verify(accommodationRepository, never()).save(any());
        }

        @Test
        void archive_byOwnerWithoutConfirmedStays_cancelsPendingAndArchives() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.ACTIVE)));
            stubConfirmedUpcoming(false);
            expectAccSaveEchoed();

            assertThat(service.archiveAccommodation(30L, owner).getStatus()).isEqualTo(AccommodationStatus.ARCHIVED);
            verify(roomReservationService).cancelForAccommodation(eq(30L),
                    eq(EnumSet.of(RoomReservationStatus.PENDING)), any());
        }

        @Test
        void archive_byOwnerAfterAdminDeactivation_throwsAccessDenied() {
            Accommodation acc = accommodation(AccommodationStatus.INACTIVE);
            acc.setDeactivatedByAdmin(true);
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(acc));

            assertThatThrownBy(() -> service.archiveAccommodation(30L, owner))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("only an administrator can archive");
            assertThat(acc.getStatus()).isEqualTo(AccommodationStatus.INACTIVE);
            verify(roomReservationService, never()).cancelForAccommodation(any(), any(), any());
            verify(accommodationRepository, never()).save(any());
        }

        @Test
        void archive_byAdminAfterAdminDeactivation_succeeds() {
            Accommodation acc = accommodation(AccommodationStatus.INACTIVE);
            acc.setDeactivatedByAdmin(true);
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(acc));
            expectAccSaveEchoed();

            assertThat(service.archiveAccommodation(30L, admin).getStatus()).isEqualTo(AccommodationStatus.ARCHIVED);
        }

        @Test
        void archive_byOwnerAfterOwnDeactivation_stillAllowed() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.INACTIVE)));
            stubConfirmedUpcoming(false);
            expectAccSaveEchoed();

            assertThat(service.archiveAccommodation(30L, owner).getStatus()).isEqualTo(AccommodationStatus.ARCHIVED);
        }

        @Test
        void archive_byAdminWithConfirmedUpcomingStay_cancelsEverything() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.ACTIVE)));
            expectAccSaveEchoed();

            assertThat(service.archiveAccommodation(30L, admin).getStatus()).isEqualTo(AccommodationStatus.ARCHIVED);
            verify(roomReservationService).cancelForAccommodation(eq(30L),
                    eq(EnumSet.of(RoomReservationStatus.PENDING, RoomReservationStatus.CONFIRMED)), any());
        }

        @Test
        void markTemporarilyUnavailable_doesNotCancelAnything() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.ACTIVE)));
            expectAccSaveEchoed();

            service.markTemporarilyUnavailable(30L, owner);

            verify(roomReservationService, never()).cancelForAccommodation(any(), any(), any());
        }
    }

    @Nested
    class AdminDeactivation {

        @Test
        void reactivate_byOwnerWhenAdminDeactivated_throwsAccessDenied() {
            Accommodation acc = accommodation(AccommodationStatus.INACTIVE);
            acc.setDeactivatedByAdmin(true);
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(acc));

            assertThatThrownBy(() -> service.reactivateAccommodation(30L, owner))
                    .isInstanceOf(AccessDeniedException.class);
            assertThat(acc.getStatus()).isEqualTo(AccommodationStatus.INACTIVE);
            verify(accommodationRepository, never()).save(any());
        }

        @Test
        void reactivate_byAdminWhenAdminDeactivated_reactivatesAndClearsFlag() {
            Accommodation acc = accommodation(AccommodationStatus.INACTIVE);
            acc.setDeactivatedByAdmin(true);
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(acc));
            expectAccSaveEchoed();

            AccommodationResponseDto result = service.reactivateAccommodation(30L, admin);

            assertThat(result.getStatus()).isEqualTo(AccommodationStatus.ACTIVE);
            assertThat(result.getDeactivatedByAdmin()).isFalse();
        }

        @Test
        void reactivate_byOwnerWhenOwnerDeactivated_reactivates() {
            Accommodation acc = accommodation(AccommodationStatus.INACTIVE);
            acc.setDeactivatedByAdmin(false);
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(acc));
            expectAccSaveEchoed();

            assertThat(service.reactivateAccommodation(30L, owner).getStatus()).isEqualTo(AccommodationStatus.ACTIVE);
        }

        @Test
        void reactivate_legacyRowWithNullFlag_ownerCanReactivate() {
            when(accommodationRepository.findById(30L)).thenReturn(Optional.of(accommodation(AccommodationStatus.INACTIVE)));
            expectAccSaveEchoed();

            assertThat(service.reactivateAccommodation(30L, owner).getStatus()).isEqualTo(AccommodationStatus.ACTIVE);
        }
    }

    // ------------------------------------------------------------------
    // search / filter / sort
    // ------------------------------------------------------------------

    private Accommodation listing(long id, String name, Integer stars) {
        return Accommodation.builder().id(id).name(name).description("d").location(galle)
                .starRating(stars).status(AccommodationStatus.ACTIVE).owner(owner).build();
    }

    private Room roomOf(Accommodation acc, String price) {
        return Room.builder().id(acc.getId() * 10).accommodation(acc).roomType("Std")
                .pricePerNight(new BigDecimal(price)).totalRooms(1).maxOccupancy(2).build();
    }

    /** Stubs the repository to return the listings, each with one room at the given price (null = no rooms). */
    private void stubSearchResult(Object... listingAndPricePairs) {
        List<Accommodation> found = new java.util.ArrayList<>();
        for (int i = 0; i < listingAndPricePairs.length; i += 2) {
            Accommodation acc = (Accommodation) listingAndPricePairs[i];
            String price = (String) listingAndPricePairs[i + 1];
            found.add(acc);
            when(roomRepository.findByAccommodationId(acc.getId()))
                    .thenReturn(price == null ? List.of() : List.of(roomOf(acc, price)));
        }
        when(accommodationRepository.search(any(), any(), any(), any(), any(), any())).thenReturn(found);
    }

    private List<String> names(List<AccommodationResponseDto> result) {
        return result.stream().map(AccommodationResponseDto::getName).toList();
    }

    @Nested
    class SearchAndSort {

        @Test
        void filters_arePassedToTheRepository_withTrimmedLowerCasedEscapedNamePattern() {
            when(accommodationRepository.search(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

            service.getAllActive(7L, "  50%_Off [Deal]!  ", 3, new BigDecimal("10"), new BigDecimal("90"), null);

            verify(accommodationRepository).search(any(), eq(7L), eq("%50!%!_off ![deal]!!%"), eq(3),
                    eq(new BigDecimal("10")), eq(new BigDecimal("90")));
        }

        @Test
        void blankName_isIgnored() {
            when(accommodationRepository.search(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

            service.getAllActive(null, "   ", null, null, null, "  ");

            verify(accommodationRepository).search(any(), eq(null), eq(null), eq(null), eq(null), eq(null));
        }

        @Test
        void onlyBrowsableStatusesAreEverRequested_whateverTheFilters() {
            when(accommodationRepository.search(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

            service.getAllActive(1L, "x", 5, BigDecimal.ONE, BigDecimal.TEN, "name");

            org.mockito.ArgumentCaptor<java.util.Collection<AccommodationStatus>> statuses =
                    org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
            verify(accommodationRepository).search(statuses.capture(), any(), any(), any(), any(), any());
            assertThat(statuses.getValue()).containsExactlyInAnyOrder(AccommodationStatus.ACTIVE,
                    AccommodationStatus.FULLY_BOOKED, AccommodationStatus.TEMPORARILY_UNAVAILABLE);
        }

        @Test
        void noSort_keepsTheRepositoryOrder() {
            stubSearchResult(listing(1, "Zed", 3), "50", listing(2, "Alpha", 5), "20");

            assertThat(names(service.getAllActive(null, null, null, null, null, null)))
                    .containsExactly("Zed", "Alpha");
        }

        @Test
        void sortByName_isCaseInsensitiveAscending() {
            stubSearchResult(listing(1, "zebra Lodge", 3), "50", listing(2, "Alpha Inn", 5), "20",
                    listing(3, "beach Hut", 2), "10");

            assertThat(names(service.getAllActive(null, null, null, null, null, "name")))
                    .containsExactly("Alpha Inn", "beach Hut", "zebra Lodge");
        }

        @Test
        void sortByPriceAsc_usesCheapestRoom_andPutsListingsWithoutRoomsLast() {
            Accommodation twoRooms = listing(1, "Two Rooms", 3);
            when(roomRepository.findByAccommodationId(1L))
                    .thenReturn(List.of(roomOf(twoRooms, "90"), roomOf(twoRooms, "30")));
            Accommodation mid = listing(2, "Mid", 3);
            when(roomRepository.findByAccommodationId(2L)).thenReturn(List.of(roomOf(mid, "60")));
            Accommodation none = listing(3, "No Rooms", 3);
            when(roomRepository.findByAccommodationId(3L)).thenReturn(List.of());
            when(accommodationRepository.search(any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(none, mid, twoRooms));

            assertThat(names(service.getAllActive(null, null, null, null, null, "price_asc")))
                    .containsExactly("Two Rooms", "Mid", "No Rooms");
        }

        @Test
        void sortByPriceDesc_usesCheapestRoom_andStillPutsListingsWithoutRoomsLast() {
            Accommodation twoRooms = listing(1, "Two Rooms", 3);
            when(roomRepository.findByAccommodationId(1L))
                    .thenReturn(List.of(roomOf(twoRooms, "90"), roomOf(twoRooms, "30")));
            Accommodation mid = listing(2, "Mid", 3);
            when(roomRepository.findByAccommodationId(2L)).thenReturn(List.of(roomOf(mid, "60")));
            Accommodation none = listing(3, "No Rooms", 3);
            when(roomRepository.findByAccommodationId(3L)).thenReturn(List.of());
            when(accommodationRepository.search(any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(none, twoRooms, mid));

            // cheapest rooms: Mid 60 > Two Rooms 30
            assertThat(names(service.getAllActive(null, null, null, null, null, "price_desc")))
                    .containsExactly("Mid", "Two Rooms", "No Rooms");
        }

        @Test
        void sortByStarsDesc_putsUnratedLast_andBreaksTiesByName() {
            stubSearchResult(listing(1, "Unrated", null), "10", listing(2, "Bravo", 4), "10",
                    listing(3, "Alpha", 4), "10", listing(4, "Top", 5), "10");

            assertThat(names(service.getAllActive(null, null, null, null, null, "stars_desc")))
                    .containsExactly("Top", "Alpha", "Bravo", "Unrated");
        }

        @Test
        void invalidParameters_throwBadRequest_andNeverQuery() {
            assertThatThrownBy(() -> service.getAllActive(null, null, 0, null, null, null))
                    .isInstanceOf(BadRequestException.class).hasMessageContaining("minStars");
            assertThatThrownBy(() -> service.getAllActive(null, null, 6, null, null, null))
                    .isInstanceOf(BadRequestException.class).hasMessageContaining("minStars");
            assertThatThrownBy(() -> service.getAllActive(null, null, null, new BigDecimal("-1"), null, null))
                    .isInstanceOf(BadRequestException.class).hasMessageContaining("negative");
            assertThatThrownBy(() -> service.getAllActive(null, null, null, null, new BigDecimal("-0.01"), null))
                    .isInstanceOf(BadRequestException.class).hasMessageContaining("negative");
            assertThatThrownBy(() -> service.getAllActive(
                    null, null, null, new BigDecimal("90"), new BigDecimal("10"), null))
                    .isInstanceOf(BadRequestException.class).hasMessageContaining("minPrice");
            assertThatThrownBy(() -> service.getAllActive(null, null, null, null, null, "cheapest"))
                    .isInstanceOf(BadRequestException.class).hasMessageContaining("sort");

            verify(accommodationRepository, never()).search(any(), any(), any(), any(), any(), any());
        }

        @Test
        void equalMinAndMaxPrice_isAllowed() {
            when(accommodationRepository.search(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

            service.getAllActive(null, null, null, new BigDecimal("50"), new BigDecimal("50"), null);

            verify(accommodationRepository).search(any(), any(), any(), any(), eq(new BigDecimal("50")),
                    eq(new BigDecimal("50")));
        }
    }

}
