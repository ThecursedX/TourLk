package com.tourlk.service;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.eq;
import com.tourlk.enums.NotificationType;
import com.tourlk.dto.VehicleHireRequestDto;
import com.tourlk.dto.VehicleHireResponseDto;
import com.tourlk.entity.User;
import com.tourlk.entity.Vehicle;
import com.tourlk.entity.VehicleHire;
import com.tourlk.enums.Role;
import com.tourlk.enums.VehicleHireStatus;
import com.tourlk.enums.VehicleStatus;
import com.tourlk.enums.VehicleType;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.InvalidDateRangeException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.exception.VehicleUnavailableException;
import com.tourlk.repo.VehicleHireRepository;
import com.tourlk.repo.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VehicleHireServiceImpl}: create (incl. server-side
 * total-price computation), confirm/cancel/complete, and the date-range,
 * availability, ownership and status guards.
 */
@ExtendWith(MockitoExtension.class)
class VehicleHireServiceImplTest {

    @Mock
    private VehicleHireRepository vehicleHireRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private VehicleHireServiceImpl service;

    private User tourist;
    private User stranger;
    private User driver;
    private User admin;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        tourist = User.builder().id(1L).name("Tess").role(Role.TOURIST).build();
        stranger = User.builder().id(2L).name("Stan").role(Role.TOURIST).build();
        driver = User.builder().id(3L).name("Dave Driver").role(Role.DRIVER).build();
        admin = User.builder().id(9L).name("Amy Admin").role(Role.ADMIN).build();
        vehicle = Vehicle.builder()
                .id(40L).driver(driver).vehicleType(VehicleType.CAR)
                .make("Toyota").model("Prius").registrationNumber("CAB-1234")
                .seatingCapacity(4).pricePerDay(new BigDecimal("100.00")).status(VehicleStatus.AVAILABLE)
                .build();
    }

    private VehicleHire hire(VehicleHireStatus status, User owner) {
        return VehicleHire.builder()
                .id(8L).tourist(owner).vehicle(vehicle)
                .startDate(LocalDate.now().plusDays(5)).endDate(LocalDate.now().plusDays(7))
                .pickupLocation("CMB Airport").totalPrice(new BigDecimal("300.00")).status(status)
                .build();
    }

    // ------------------------------------------------------------------
    // createHire
    // ------------------------------------------------------------------

    @Test
    void createHire_validAndAvailable_savesPendingWithComputedTotalPrice() {
        when(vehicleRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(vehicle));
        when(vehicleHireRepository.existsOverlapping(any(), any(), any(), any())).thenReturn(false);
        when(vehicleHireRepository.save(any(VehicleHire.class))).thenAnswer(inv -> {
            VehicleHire h = inv.getArgument(0);
            h.setId(8L);
            return h;
        });

        // 3 inclusive days x 100.00 => 300.00
        VehicleHireRequestDto request = new VehicleHireRequestDto(
                40L, LocalDate.now().plusDays(5), LocalDate.now().plusDays(7), "CMB Airport", null);
        VehicleHireResponseDto result = service.createHire(request, tourist);

        assertThat(result.getStatus()).isEqualTo(VehicleHireStatus.PENDING);
        assertThat(result.getTotalPrice()).isEqualByComparingTo("300.00");
        assertThat(result.getTouristId()).isEqualTo(1L);
    }

    @Test
    void createHire_endBeforeStart_throwsInvalidDateRange() {
        VehicleHireRequestDto request = new VehicleHireRequestDto(
                40L, LocalDate.now().plusDays(7), LocalDate.now().plusDays(5), "CMB Airport", null);

        assertThatThrownBy(() -> service.createHire(request, tourist))
                .isInstanceOf(InvalidDateRangeException.class);
        verify(vehicleHireRepository, never()).save(any());
    }

    @Test
    void createHire_vehicleNotActive_throwsBadRequest() {
        vehicle.setStatus(VehicleStatus.UNDER_MAINTENANCE);
        when(vehicleRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(vehicle));

        VehicleHireRequestDto request = new VehicleHireRequestDto(
                40L, LocalDate.now().plusDays(5), LocalDate.now().plusDays(7), "CMB Airport", null);

        assertThatThrownBy(() -> service.createHire(request, tourist))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createHire_overlappingConfirmedHire_throwsVehicleUnavailable() {
        when(vehicleRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(vehicle));
        when(vehicleHireRepository.existsOverlapping(any(), any(), any(), any())).thenReturn(true);

        VehicleHireRequestDto request = new VehicleHireRequestDto(
                40L, LocalDate.now().plusDays(5), LocalDate.now().plusDays(7), "CMB Airport", null);

        assertThatThrownBy(() -> service.createHire(request, tourist))
                .isInstanceOf(VehicleUnavailableException.class);
        verify(vehicleHireRepository, never()).save(any());
    }

    @Test
    void createHire_vehicleNotFound_throwsResourceNotFound() {
        when(vehicleRepository.findByIdForUpdate(40L)).thenReturn(Optional.empty());

        VehicleHireRequestDto request = new VehicleHireRequestDto(
                40L, LocalDate.now().plusDays(5), LocalDate.now().plusDays(7), "CMB Airport", null);

        assertThatThrownBy(() -> service.createHire(request, tourist))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // confirmHire — ownership + status
    // ------------------------------------------------------------------

    @Test
    void confirmHire_byVehicleOwner_confirms() {
        when(vehicleHireRepository.findById(8L)).thenReturn(Optional.of(hire(VehicleHireStatus.PENDING, tourist)));
        when(vehicleRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(vehicle));
        when(vehicleHireRepository.existsOverlapping(any(), any(), any(), any())).thenReturn(false);
        when(vehicleHireRepository.save(any(VehicleHire.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.confirmHire(8L, driver).getStatus()).isEqualTo(VehicleHireStatus.CONFIRMED);
    }

    @Test
    void confirmHire_byUnrelatedUser_throwsAccessDenied() {
        when(vehicleHireRepository.findById(8L)).thenReturn(Optional.of(hire(VehicleHireStatus.PENDING, tourist)));
        when(vehicleRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> service.confirmHire(8L, stranger))
                .isInstanceOf(AccessDeniedException.class);
        verify(vehicleHireRepository, never()).save(any());
    }

    @Test
    void confirmHireAfterPayment_pending_confirmsWithoutOwnerCheck() {
        when(vehicleHireRepository.findById(8L)).thenReturn(Optional.of(hire(VehicleHireStatus.PENDING, tourist)));
        when(vehicleRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(vehicle));
        when(vehicleHireRepository.existsOverlapping(any(), any(), any(), any())).thenReturn(false);
        when(vehicleHireRepository.save(any(VehicleHire.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.confirmHireAfterPayment(8L).getStatus()).isEqualTo(VehicleHireStatus.CONFIRMED);
    }

    // ------------------------------------------------------------------
    // cancelHire / completeHire / getHireById
    // ------------------------------------------------------------------

    @Test
    void cancelHire_byTourist_cancels() {
        when(vehicleHireRepository.findById(8L)).thenReturn(Optional.of(hire(VehicleHireStatus.PENDING, tourist)));
        when(vehicleHireRepository.save(any(VehicleHire.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.cancelHire(8L, tourist).getStatus()).isEqualTo(VehicleHireStatus.CANCELLED);
    }

    @Test
    void cancelHire_alreadyCompleted_throwsInvalidStatusTransition() {
        when(vehicleHireRepository.findById(8L)).thenReturn(Optional.of(hire(VehicleHireStatus.COMPLETED, tourist)));

        assertThatThrownBy(() -> service.cancelHire(8L, admin))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void completeHire_confirmedByOwner_completes() {
        when(vehicleHireRepository.findById(8L)).thenReturn(Optional.of(hire(VehicleHireStatus.CONFIRMED, tourist)));
        when(vehicleHireRepository.save(any(VehicleHire.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.completeHire(8L, driver).getStatus()).isEqualTo(VehicleHireStatus.COMPLETED);
    }

    @Test
    void getHireById_byUnrelatedUser_throwsAccessDenied() {
        when(vehicleHireRepository.findById(8L)).thenReturn(Optional.of(hire(VehicleHireStatus.PENDING, tourist)));

        assertThatThrownBy(() -> service.getHireById(8L, stranger))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getHireById_notFound_throwsResourceNotFound() {
        when(vehicleHireRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHireById(404L, tourist))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // in-app notifications
    // ------------------------------------------------------------------

    @Test
    void createHire_notifiesTheVehicleOwner() {
        when(vehicleRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(vehicle));
        when(vehicleHireRepository.existsOverlapping(any(), any(), any(), any())).thenReturn(false);
        when(vehicleHireRepository.save(any(VehicleHire.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createHire(new VehicleHireRequestDto(
                40L, LocalDate.now().plusDays(5), LocalDate.now().plusDays(7), "CMB Airport", null), tourist);

        verify(notificationService).notify(eq(driver), eq(NotificationType.VEHICLE_HIRE_REQUESTED), any(), any(),
                eq("/vehicles/owner/hires"));
    }

    @Test
    void confirmHire_byOwner_notifiesOnlyTheTourist() {
        when(vehicleHireRepository.findById(8L)).thenReturn(Optional.of(hire(VehicleHireStatus.PENDING, tourist)));
        when(vehicleRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(vehicle));
        when(vehicleHireRepository.existsOverlapping(any(), any(), any(), any())).thenReturn(false);
        when(vehicleHireRepository.save(any(VehicleHire.class))).thenAnswer(inv -> inv.getArgument(0));

        service.confirmHire(8L, driver);

        verify(notificationService).notify(eq(tourist), eq(NotificationType.VEHICLE_HIRE_CONFIRMED), any(), any(), eq("/hires/mine"));
        verify(notificationService, never()).notify(eq(driver), any(), any(), any(), any());
    }

    @Test
    void confirmHireAfterPayment_notifiesBothTouristAndOwner() {
        when(vehicleHireRepository.findById(8L)).thenReturn(Optional.of(hire(VehicleHireStatus.PENDING, tourist)));
        when(vehicleRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(vehicle));
        when(vehicleHireRepository.existsOverlapping(any(), any(), any(), any())).thenReturn(false);
        when(vehicleHireRepository.save(any(VehicleHire.class))).thenAnswer(inv -> inv.getArgument(0));

        service.confirmHireAfterPayment(8L);

        verify(notificationService).notify(eq(tourist), eq(NotificationType.VEHICLE_HIRE_CONFIRMED), any(), any(), any());
        verify(notificationService).notify(eq(driver), eq(NotificationType.VEHICLE_HIRE_CONFIRMED), any(), any(), any());
    }

    @Test
    void cancelHire_byTourist_notifiesTheOwner_andByOwnerNotifiesTheTourist() {
        when(vehicleHireRepository.findById(8L)).thenReturn(Optional.of(hire(VehicleHireStatus.CONFIRMED, tourist)));
        when(vehicleHireRepository.save(any(VehicleHire.class))).thenAnswer(inv -> inv.getArgument(0));
        service.cancelHire(8L, tourist);
        verify(notificationService).notify(eq(driver), eq(NotificationType.VEHICLE_HIRE_CANCELLED), any(), any(), any());

        when(vehicleHireRepository.findById(8L)).thenReturn(Optional.of(hire(VehicleHireStatus.CONFIRMED, tourist)));
        service.cancelHire(8L, driver);
        verify(notificationService).notify(eq(tourist), eq(NotificationType.VEHICLE_HIRE_CANCELLED), any(), any(), any());
    }

    @Test
    void completeHire_notifiesTheTourist() {
        when(vehicleHireRepository.findById(8L)).thenReturn(Optional.of(hire(VehicleHireStatus.CONFIRMED, tourist)));
        when(vehicleHireRepository.save(any(VehicleHire.class))).thenAnswer(inv -> inv.getArgument(0));

        service.completeHire(8L, driver);

        verify(notificationService).notify(eq(tourist), eq(NotificationType.VEHICLE_HIRE_COMPLETED), any(), any(), any());
    }
}
