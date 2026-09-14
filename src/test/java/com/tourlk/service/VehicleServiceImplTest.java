package com.tourlk.service;

import com.tourlk.dto.VehicleRequestDto;
import com.tourlk.dto.VehicleResponseDto;
import com.tourlk.entity.User;
import com.tourlk.entity.Vehicle;
import com.tourlk.enums.Role;
import com.tourlk.enums.VehicleStatus;
import com.tourlk.enums.VehicleType;
import com.tourlk.exception.InvalidStatusTransitionException;
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

    @InjectMocks
    private VehicleServiceImpl service;

    private User driver;
    private User stranger;
    private User admin;

    @BeforeEach
    void setUp() {
        driver = User.builder().id(1L).name("Dave Driver").role(Role.DRIVER).build();
        stranger = User.builder().id(2L).name("Other Driver").role(Role.DRIVER).build();
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
        return new VehicleRequestDto(VehicleType.VAN, "Toyota", "HiAce", "NA-1234", 9, new BigDecimal("90.00"));
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
        void createVehicle_savesAsPendingApprovalOwnedByDriver() {
            expectSaveEchoed();

            VehicleResponseDto result = service.createVehicle(request(), driver);

            assertThat(result.getStatus()).isEqualTo(VehicleStatus.PENDING_APPROVAL);
            assertThat(result.getDriverId()).isEqualTo(1L);
        }

        @Test
        void updateVehicle_byOwner_appliesChanges() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.ACTIVE)));
            expectSaveEchoed();

            VehicleRequestDto edit = new VehicleRequestDto(
                    VehicleType.CAR, "Honda", "Fit", "NB-5678", 4, new BigDecimal("60.00"));
            VehicleResponseDto result = service.updateVehicle(20L, edit, driver);

            assertThat(result.getMake()).isEqualTo("Honda");
            assertThat(result.getSeatingCapacity()).isEqualTo(4);
        }

        @Test
        void updateVehicle_byUnrelatedDriver_throwsAccessDenied() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.ACTIVE)));

            assertThatThrownBy(() -> service.updateVehicle(20L, request(), stranger))
                    .isInstanceOf(AccessDeniedException.class);
            verify(vehicleRepository, never()).save(any());
        }
    }

    @Nested
    class ApprovalWorkflow {

        @Test
        void approveVehicle_pendingApproval_becomesActive() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.PENDING_APPROVAL)));
            expectSaveEchoed();

            assertThat(service.approveVehicle(20L).getStatus()).isEqualTo(VehicleStatus.ACTIVE);
        }

        @Test
        void approveVehicle_notPending_throwsInvalidStatusTransition() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.ACTIVE)));

            assertThatThrownBy(() -> service.approveVehicle(20L))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void rejectVehicle_pendingApproval_becomesArchived() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.PENDING_APPROVAL)));
            expectSaveEchoed();

            assertThat(service.rejectVehicle(20L).getStatus()).isEqualTo(VehicleStatus.ARCHIVED);
        }
    }

    @Nested
    class StatusTransitions {

        @Test
        void deactivateVehicle_active_becomesInactive() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.ACTIVE)));
            expectSaveEchoed();

            assertThat(service.deactivateVehicle(20L, driver).getStatus()).isEqualTo(VehicleStatus.INACTIVE);
        }

        @Test
        void reactivateVehicle_inactive_becomesActive() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.INACTIVE)));
            expectSaveEchoed();

            assertThat(service.reactivateVehicle(20L, admin).getStatus()).isEqualTo(VehicleStatus.ACTIVE);
        }

        @Test
        void archiveVehicle_alreadyArchived_throwsInvalidStatusTransition() {
            when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle(VehicleStatus.ARCHIVED)));

            assertThatThrownBy(() -> service.archiveVehicle(20L, driver))
                    .isInstanceOf(InvalidStatusTransitionException.class);
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
}
