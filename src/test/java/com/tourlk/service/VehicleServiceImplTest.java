package com.tourlk.service;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.eq;
import com.tourlk.enums.NotificationType;
import com.tourlk.dto.VehicleRequestDto;
import com.tourlk.dto.VehicleResponseDto;
import com.tourlk.entity.User;
import com.tourlk.entity.Vehicle;
import com.tourlk.enums.Role;
import com.tourlk.enums.VehicleStatus;
import com.tourlk.enums.VehicleType;
import com.tourlk.enums.VerificationStatus;
import com.tourlk.exception.InvalidDateRangeException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.LicenceNotVerifiedException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.VehicleRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VehicleServiceImpl}: registration, the
 * PENDING_APPROVAL approval workflow, and the driver/admin management guard.
 */
@ExtendWith(MockitoExtension.class)
class VehicleServiceImplTest {

    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private VehicleServiceImpl service;

    private User driver;
    private User stranger;
    private User admin;

    @BeforeEach
    void setUp() {
        driver = User.builder().id(1L).name("Dave Driver").role(Role.DRIVER)
                .verificationStatus(VerificationStatus.VERIFIED).build();
        stranger = User.builder().id(2L).name("Other Driver").role(Role.DRIVER)
                .verificationStatus(VerificationStatus.VERIFIED).build();
        admin = User.builder().id(9L).name("Amy Admin").role(Role.ADMIN).build();
    }

    private Vehicle vehicle(VehicleStatus status) {
        return Vehicle.builder()
                .id(20L).driver(driver).vehicleType(VehicleType.VAN)
                .make("Toyota").model("HiAce").registrationNumber("NA-1234")
                .seatingCapacity(9).pricePerDay(new BigDecimal("90.00")).status(status)
                .build();
    }

    private VehicleRequestDto request() {
        return request(VehicleType.VAN, "Toyota", "HiAce", "NA-1234", 9, "90.00");
    }

    private VehicleRequestDto request(VehicleType type, String make, String model, String reg, int seats, String price) {
        VehicleRequestDto dto = new VehicleRequestDto();
        dto.setVehicleType(type);
        dto.setMake(make);
        dto.setModel(model);
        dto.setRegistrationNumber(reg);
        dto.setSeatingCapacity(seats);
        dto.setPricePerDay(new BigDecimal(price));
        return dto;
    }

    private void expectSaveEchoed() {
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> {
            Vehicle v = inv.getArgument(0);
            if (v.getId() == null) {
                v.setId(20L);
            }
            return v;
        });
    }

    @Nested
    class CreateAndUpdate {

        @Test
        void createVehicle_savesAsPendingVerificationOwnedByDriver() {
            expectSaveEchoed();

            VehicleResponseDto result = service.createVehicle(request(), driver);

            assertThat(result.getStatus()).isEqualTo(VehicleStatus.PENDING_VERIFICATION);
            assertThat(result.getDriverId()).isEqualTo(1L);
        }

        @Test
        void createVehicle_byUnverifiedDriver_throwsLicenceNotVerified() {
            driver.setVerificationStatus(VerificationStatus.NOT_SUBMITTED);

            assertThatThrownBy(() -> service.createVehicle(request(), driver))
                    .isInstanceOf(LicenceNotVerifiedException.class);
            verify(vehicleRepository, never()).save(any());
        }

        @Test
        void createVehicle_byAdmin_skipsLicenceCheck() {
            expectSaveEchoed();

            assertThat(service.createVehicle(request(), admin).getStatus()).isEqualTo(VehicleStatus.PENDING_VERIFICATION);
        }

        @Test
        void updateVehicle_byOwner_appliesChanges() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.AVAILABLE)));
            expectSaveEchoed();

            VehicleRequestDto edit = request(VehicleType.CAR, "Honda", "Fit", "NB-5678", 4, "60.00");
            VehicleResponseDto result = service.updateVehicle(20L, edit, driver);

            assertThat(result.getMake()).isEqualTo("Honda");
            assertThat(result.getSeatingCapacity()).isEqualTo(4);
        }

        @Test
        void updateVehicle_byUnrelatedDriver_throwsAccessDenied() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.AVAILABLE)));

            assertThatThrownBy(() -> service.updateVehicle(20L, request(), stranger))
                    .isInstanceOf(AccessDeniedException.class);
            verify(vehicleRepository, never()).save(any());
        }
    }

    @Nested
    class ApprovalWorkflow {

        @Test
        void approveVehicle_pendingVerification_becomesAvailable() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.PENDING_VERIFICATION)));
            expectSaveEchoed();

            assertThat(service.approveVehicle(20L).getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
        }

        @Test
        void approveVehicle_notPending_throwsInvalidStatusTransition() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.AVAILABLE)));

            assertThatThrownBy(() -> service.approveVehicle(20L))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void rejectVehicle_pendingVerification_revertsToDraft() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.PENDING_VERIFICATION)));
            expectSaveEchoed();

            assertThat(service.rejectVehicle(20L).getStatus()).isEqualTo(VehicleStatus.DRAFT);
        }
    }

    @Nested
    class StatusTransitions {

        @Test
        void deactivateVehicle_available_becomesOutOfService() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.AVAILABLE)));
            expectSaveEchoed();

            assertThat(service.deactivateVehicle(20L, driver).getStatus()).isEqualTo(VehicleStatus.OUT_OF_SERVICE);
        }

        @Test
        void reactivateVehicle_outOfService_becomesAvailable() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.OUT_OF_SERVICE)));
            expectSaveEchoed();

            assertThat(service.reactivateVehicle(20L, admin).getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
        }

        @Test
        void archiveVehicle_alreadyArchived_throwsInvalidStatusTransition() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.ARCHIVED)));

            assertThatThrownBy(() -> service.archiveVehicle(20L, driver))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }

    @Nested
    class ExtendedFieldsAndLifecycle {

        @Test
        void createVehicle_persistsAndReturnsExtendedFields() {
            expectSaveEchoed();
            VehicleRequestDto dto = request();
            dto.setAirConditioned(true);
            dto.setFacilities(List.of("WiFi", "USB charging"));
            dto.setImageUrls(List.of("http://img/1.jpg", "http://img/2.jpg"));
            dto.setDriverName("  Kamal  ");
            dto.setDriverPhone("0771234567");
            dto.setInsuranceExpiry(LocalDate.of(2027, 1, 31));
            dto.setLastMaintenanceDate(LocalDate.of(2026, 6, 1));
            dto.setNextMaintenanceDate(LocalDate.of(2026, 12, 1));

            VehicleResponseDto result = service.createVehicle(dto, driver);

            assertThat(result.isAirConditioned()).isTrue();
            assertThat(result.getFacilities()).containsExactly("WiFi", "USB charging");
            assertThat(result.getImageUrls()).containsExactly("http://img/1.jpg", "http://img/2.jpg");
            assertThat(result.getDriverName()).isEqualTo("Kamal");
            assertThat(result.getDriverPhone()).isEqualTo("0771234567");
            assertThat(result.getInsuranceExpiry()).isEqualTo(LocalDate.of(2027, 1, 31));
            assertThat(result.getNextMaintenanceDate()).isEqualTo(LocalDate.of(2026, 12, 1));
        }

        @Test
        void createVehicle_withoutOptionalFields_fallsBackToOwnerAndDefaults() {
            driver.setPhone("0112223333");
            expectSaveEchoed();

            VehicleResponseDto result = service.createVehicle(request(), driver);

            assertThat(result.isAirConditioned()).isFalse();
            assertThat(result.getFacilities()).isEmpty();
            assertThat(result.getImageUrls()).isEmpty();
            assertThat(result.getDriverName()).isEqualTo("Dave Driver");
            assertThat(result.getDriverPhone()).isEqualTo("0112223333");
        }

        @Test
        void createVehicle_nextMaintenanceBeforeLast_throwsInvalidDateRange() {
            VehicleRequestDto dto = request();
            dto.setLastMaintenanceDate(LocalDate.of(2026, 6, 1));
            dto.setNextMaintenanceDate(LocalDate.of(2026, 5, 1));

            assertThatThrownBy(() -> service.createVehicle(dto, driver))
                    .isInstanceOf(InvalidDateRangeException.class);
            verify(vehicleRepository, never()).save(any());
        }

        @Test
        void submitForVerification_draftByOwner_becomesPendingVerification() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.DRAFT)));
            expectSaveEchoed();

            assertThat(service.submitForVerification(20L, driver).getStatus())
                    .isEqualTo(VehicleStatus.PENDING_VERIFICATION);
        }

        @Test
        void submitForVerification_notDraft_throwsInvalidStatusTransition() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.AVAILABLE)));

            assertThatThrownBy(() -> service.submitForVerification(20L, driver))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void startMaintenance_available_becomesUnderMaintenance() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.AVAILABLE)));
            expectSaveEchoed();

            assertThat(service.startMaintenance(20L, driver).getStatus())
                    .isEqualTo(VehicleStatus.UNDER_MAINTENANCE);
        }

        @Test
        void startMaintenance_outOfService_throwsInvalidStatusTransition() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.OUT_OF_SERVICE)));

            assertThatThrownBy(() -> service.startMaintenance(20L, driver))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void reactivateVehicle_underMaintenance_becomesAvailable() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.UNDER_MAINTENANCE)));
            expectSaveEchoed();

            assertThat(service.reactivateVehicle(20L, driver).getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
        }

        @Test
        void archiveVehicle_outOfService_isNeverDeleted_evenByAdmin() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.OUT_OF_SERVICE)));

            assertThatThrownBy(() -> service.archiveVehicle(20L, admin))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("Out-of-service");
            verify(vehicleRepository, never()).save(any());
            verify(vehicleRepository, never()).delete(any());
        }

        @Test
        void getAllActive_searchesBrowsableStatusesOnly() {
            when(vehicleRepository.search(any(), any(), any())).thenReturn(List.of());

            service.getAllActive(null, null);

            org.mockito.ArgumentCaptor<java.util.Collection<VehicleStatus>> statuses =
                    org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
            verify(vehicleRepository).search(statuses.capture(), any(), any());
            assertThat(statuses.getValue())
                    .containsExactlyInAnyOrder(VehicleStatus.AVAILABLE, VehicleStatus.BOOKED);
        }
    }

    @Nested
    class Queries {

        @Test
        void getById_notFound_throwsResourceNotFound() {
            when(vehicleRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(404L)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Notifications {

        @Test
        void approveVehicle_notifiesTheDriver() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.PENDING_VERIFICATION)));
            expectSaveEchoed();

            service.approveVehicle(20L);

            verify(notificationService).notify(eq(driver), eq(NotificationType.VEHICLE_APPROVED), any(), any(), eq("/vehicles/mine"));
        }

        @Test
        void rejectVehicle_notifiesTheDriver() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.PENDING_VERIFICATION)));
            expectSaveEchoed();

            service.rejectVehicle(20L);

            verify(notificationService).notify(eq(driver), eq(NotificationType.VEHICLE_REJECTED), any(), any(), any());
        }

        @Test
        void deactivateVehicle_byAdmin_notifiesTheOwner() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.AVAILABLE)));
            expectSaveEchoed();

            service.deactivateVehicle(20L, admin);

            verify(notificationService).notify(eq(driver), eq(NotificationType.VEHICLE_STATUS_CHANGED), any(), any(), any());
        }

        @Test
        void deactivateVehicle_byOwner_doesNotNotifyThemselves() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.AVAILABLE)));
            expectSaveEchoed();

            service.deactivateVehicle(20L, driver);

            verifyNoInteractions(notificationService);
        }

        @Test
        void startMaintenanceAndReactivate_byAdmin_notifyTheOwner() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.AVAILABLE)));
            expectSaveEchoed();
            service.startMaintenance(20L, admin);

            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.OUT_OF_SERVICE)));
            service.reactivateVehicle(20L, admin);

            verify(notificationService, org.mockito.Mockito.times(2))
                    .notify(eq(driver), eq(NotificationType.VEHICLE_STATUS_CHANGED), any(), any(), any());
        }
    }
}
