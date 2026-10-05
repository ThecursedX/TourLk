package com.tourlk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tourlk.dto.PackageAddOnItemDto;
import com.tourlk.dto.PackageAddOnResponseDto;
import com.tourlk.entity.Accommodation;
import com.tourlk.entity.Destination;
import com.tourlk.entity.PackageAddOn;
import com.tourlk.entity.Room;
import com.tourlk.entity.TourPackage;
import com.tourlk.entity.User;
import com.tourlk.entity.Vehicle;
import com.tourlk.enums.AccommodationStatus;
import com.tourlk.enums.PackageStatus;
import com.tourlk.enums.Role;
import com.tourlk.enums.VehicleStatus;
import com.tourlk.enums.VehicleType;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.PackageAddOnRepository;
import com.tourlk.repo.RoomRepository;
import com.tourlk.repo.TourPackageRepository;
import com.tourlk.repo.VehicleRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class PackageAddOnServiceImplTest {

    @Mock private PackageAddOnRepository addOnRepository;
    @Mock private TourPackageRepository tourPackageRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private com.tourlk.repo.RoomReservationRepository roomReservationRepository;
    @Mock private com.tourlk.repo.VehicleHireRepository vehicleHireRepository;

    @InjectMocks private PackageAddOnServiceImpl service;

    private User guide;
    private User otherGuide;
    private User admin;
    private Destination kandy;
    private TourPackage tourPackage;

    @BeforeEach
    void setUp() {
        guide = User.builder().id(3L).role(Role.GUIDE).build();
        otherGuide = User.builder().id(4L).role(Role.GUIDE).build();
        admin = User.builder().id(9L).role(Role.ADMIN).build();
        kandy = Destination.builder().id(1L).name("Kandy").build();
        tourPackage = TourPackage.builder().id(100L).title("Hill Country").destination(kandy)
                .status(PackageStatus.ACTIVE).createdBy(guide).build();
        when(tourPackageRepository.findById(100L)).thenReturn(Optional.of(tourPackage));
    }

    private Room roomIn(Destination destination, AccommodationStatus status) {
        Accommodation hotel = Accommodation.builder().id(50L).name("Ocean View").location(destination)
                .status(status).build();
        return Room.builder().id(7L).accommodation(hotel).roomType("Deluxe")
                .pricePerNight(new BigDecimal("80.00")).totalRooms(3).maxOccupancy(2).build();
    }

    private Vehicle vehicle(VehicleStatus status) {
        return Vehicle.builder().id(8L).make("Toyota").model("HiAce").vehicleType(VehicleType.VAN)
                .seatingCapacity(9).pricePerDay(new BigDecimal("90.00")).status(status).build();
    }

    @Test
    void replaceAddOns_roomAtThePackageDestination_andAvailableVehicle_areSaved() {
        when(roomRepository.findById(7L)).thenReturn(Optional.of(roomIn(kandy, AccommodationStatus.ACTIVE)));
        when(vehicleRepository.findById(8L)).thenReturn(Optional.of(vehicle(VehicleStatus.AVAILABLE)));
        when(addOnRepository.findByTourPackageId(100L)).thenReturn(List.of());
        when(addOnRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<PackageAddOnResponseDto> result = service.replaceAddOns(100L,
                List.of(new PackageAddOnItemDto(7L, null, "Breakfast included"),
                        new PackageAddOnItemDto(null, 8L, null)), guide);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRoom().getAccommodationName()).isEqualTo("Ocean View");
        assertThat(result.get(0).getNote()).isEqualTo("Breakfast included");
        assertThat(result.get(1).getVehicle().getMake()).isEqualTo("Toyota");
    }

    @Test
    void replaceAddOns_roomInAnotherDestination_isRejected() {
        Destination galle = Destination.builder().id(2L).name("Galle").build();
        when(roomRepository.findById(7L)).thenReturn(Optional.of(roomIn(galle, AccommodationStatus.ACTIVE)));

        assertThatThrownBy(() -> service.replaceAddOns(100L, List.of(new PackageAddOnItemDto(7L, null, null)), guide))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Kandy");
        verify(addOnRepository, never()).saveAll(any());
    }

    @Test
    void replaceAddOns_inactiveAccommodation_isRejected() {
        when(roomRepository.findById(7L)).thenReturn(Optional.of(roomIn(kandy, AccommodationStatus.DRAFT)));

        assertThatThrownBy(() -> service.replaceAddOns(100L, List.of(new PackageAddOnItemDto(7L, null, null)), guide))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void replaceAddOns_vehicleNotAvailable_isRejected() {
        when(vehicleRepository.findById(8L)).thenReturn(Optional.of(vehicle(VehicleStatus.PENDING_VERIFICATION)));

        assertThatThrownBy(() -> service.replaceAddOns(100L, List.of(new PackageAddOnItemDto(null, 8L, null)), guide))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void replaceAddOns_itemMustBeExactlyOneOfRoomOrVehicle() {
        assertThatThrownBy(() -> service.replaceAddOns(100L, List.of(new PackageAddOnItemDto(7L, 8L, null)), guide))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.replaceAddOns(100L, List.of(new PackageAddOnItemDto(null, null, null)), guide))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void replaceAddOns_sameRoomTwice_isRejected() {
        when(roomRepository.findById(7L)).thenReturn(Optional.of(roomIn(kandy, AccommodationStatus.ACTIVE)));

        assertThatThrownBy(() -> service.replaceAddOns(100L,
                List.of(new PackageAddOnItemDto(7L, null, null), new PackageAddOnItemDto(7L, null, null)), guide))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void replaceAddOns_byAnotherGuide_isForbidden_butAdminMay() {
        assertThatThrownBy(() -> service.replaceAddOns(100L, List.of(), otherGuide))
                .isInstanceOf(AccessDeniedException.class);

        when(addOnRepository.findByTourPackageId(100L)).thenReturn(List.of(new PackageAddOn()));
        when(addOnRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.replaceAddOns(100L, List.of(), admin)).isEmpty();
    }

    @Test
    void getAddOns_hiddenPackage_is404ForStrangers() {
        tourPackage.setStatus(PackageStatus.DRAFT);

        assertThatThrownBy(() -> service.getAddOns(100L, null)).isInstanceOf(ResourceNotFoundException.class);
        when(addOnRepository.findByTourPackageId(100L)).thenReturn(List.of());
        assertThat(service.getAddOns(100L, guide)).isEmpty();
    }

    @Test
    void getAvailability_countsPendingAndConfirmedHoldsOverTheDerivedDates() {
        tourPackage.setDurationDays(4);
        java.time.LocalDate travel = java.time.LocalDate.now().plusDays(30);
        Room room = roomIn(kandy, AccommodationStatus.ACTIVE);
        Vehicle van = vehicle(VehicleStatus.AVAILABLE);
        when(addOnRepository.findByTourPackageId(100L)).thenReturn(List.of(
                PackageAddOn.builder().tourPackage(tourPackage).room(room).build(),
                PackageAddOn.builder().tourPackage(tourPackage).vehicle(van).build()));
        // 3 rooms in total, 3 held for check-in .. check-out (travel + 3 nights) -> none left
        when(roomReservationRepository.sumReservedRoomsOverlapping(7L,
                java.util.EnumSet.of(com.tourlk.enums.RoomReservationStatus.PENDING,
                        com.tourlk.enums.RoomReservationStatus.CONFIRMED),
                travel, travel.plusDays(3), 0L)).thenReturn(3);
        when(vehicleHireRepository.existsOverlapping(8L,
                java.util.EnumSet.of(com.tourlk.enums.VehicleHireStatus.PENDING,
                        com.tourlk.enums.VehicleHireStatus.CONFIRMED),
                travel, travel.plusDays(3), 0L)).thenReturn(false);

        var result = service.getAvailability(100L, travel, null);

        assertThat(result.get(0).getRoomId()).isEqualTo(7L);
        assertThat(result.get(0).isAvailable()).isFalse();
        assertThat(result.get(0).getRoomsLeft()).isZero();
        assertThat(result.get(1).getVehicleId()).isEqualTo(8L);
        assertThat(result.get(1).isAvailable()).isTrue();
    }

}
