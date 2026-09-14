package com.tourlk.service;

import com.tourlk.dto.TourPackageRequestDto;
import com.tourlk.dto.TourPackageResponseDto;
import com.tourlk.entity.Destination;
import com.tourlk.entity.TourPackage;
import com.tourlk.entity.User;
import com.tourlk.enums.DestinationStatus;
import com.tourlk.enums.PackageStatus;
import com.tourlk.enums.Role;
import com.tourlk.exception.DestinationInactiveException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.TourPackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TourPackageServiceImpl}: the DRAFT ->
 * PENDING_APPROVAL -> ACTIVE approval workflow, the owner/admin management
 * guard, and status-transition rules. {@link TourPackageRepository} is mocked.
 */
@ExtendWith(MockitoExtension.class)
class TourPackageServiceImplTest {

    @Mock
    private TourPackageRepository tourPackageRepository;
    @Mock
    private DestinationService destinationService;

    @InjectMocks
    private TourPackageServiceImpl service;

    private User owner;
    private User stranger;
    private User admin;
    private Destination ella;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).name("Gina Guide").email("gina@example.com").role(Role.GUIDE).build();
        stranger = User.builder().id(2L).name("Other Guide").role(Role.GUIDE).build();
        admin = User.builder().id(9L).name("Amy Admin").role(Role.ADMIN).build();
        ella = Destination.builder()
                .id(1L).name("Ella").region("Uva Province").status(DestinationStatus.ACTIVE)
                .build();
        // create/update resolve the destination by id; not every test hits that path.
        lenient().when(destinationService.requireSelectableDestination(1L)).thenReturn(ella);
    }

    private TourPackage pkg(PackageStatus status) {
        return TourPackage.builder()
                .id(10L).title("Hill Country").description("Tea trails").destination(ella)
                .durationDays(3).price(new BigDecimal("150.00")).maxCapacity(12)
                .status(status).createdBy(owner)
                .build();
    }

    private TourPackageRequestDto request() {
        return new TourPackageRequestDto("Hill Country", "Tea trails", 1L, 3, new BigDecimal("150.00"), 12);
    }

    private void expectSaveEchoed() {
        when(tourPackageRepository.save(any(TourPackage.class))).thenAnswer(inv -> {
            TourPackage p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(10L);
            }
            return p;
        });
    }

    @Nested
    class CreatePackage {

        @Test
        void createPackage_savesAsDraftOwnedByCurrentUser() {
            expectSaveEchoed();

            TourPackageResponseDto result = service.createPackage(request(), owner);

            assertThat(result.getStatus()).isEqualTo(PackageStatus.DRAFT);
            assertThat(result.getCreatedById()).isEqualTo(1L);
        }

        @Test
        void createPackage_inactiveDestination_propagatesDestinationInactive() {
            when(destinationService.requireSelectableDestination(1L))
                    .thenThrow(new DestinationInactiveException("Destination 'Ella' is inactive"));

            assertThatThrownBy(() -> service.createPackage(request(), owner))
                    .isInstanceOf(DestinationInactiveException.class);
            verify(tourPackageRepository, never()).save(any());
        }
    }

    @Nested
    class UpdatePackage {

        @Test
        void updatePackage_draftByOwner_appliesChanges() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.DRAFT)));
            expectSaveEchoed();

            TourPackageRequestDto edit = new TourPackageRequestDto(
                    "New Title", "New desc", 1L, 4, new BigDecimal("200.00"), 20);
            TourPackageResponseDto result = service.updatePackage(10L, edit, owner);

            assertThat(result.getTitle()).isEqualTo("New Title");
            assertThat(result.getMaxCapacity()).isEqualTo(20);
        }

        @Test
        void updatePackage_activeByAdmin_isAllowed() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.ACTIVE)));
            expectSaveEchoed();

            assertThat(service.updatePackage(10L, request(), admin).getStatus()).isEqualTo(PackageStatus.ACTIVE);
        }

        @Test
        void updatePackage_byUnrelatedGuide_throwsAccessDenied() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.DRAFT)));

            assertThatThrownBy(() -> service.updatePackage(10L, request(), stranger))
                    .isInstanceOf(AccessDeniedException.class);
            verify(tourPackageRepository, never()).save(any());
        }

        @Test
        void updatePackage_whilePendingApproval_throwsInvalidStatusTransition() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.PENDING_APPROVAL)));

            assertThatThrownBy(() -> service.updatePackage(10L, request(), owner))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void updatePackage_notFound_throwsResourceNotFound() {
            when(tourPackageRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updatePackage(404L, request(), owner))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class ApprovalWorkflow {

        @Test
        void submitForApproval_draftByOwner_movesToPendingApproval() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.DRAFT)));
            expectSaveEchoed();

            assertThat(service.submitForApproval(10L, owner).getStatus())
                    .isEqualTo(PackageStatus.PENDING_APPROVAL);
        }

        @Test
        void submitForApproval_notDraft_throwsInvalidStatusTransition() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.ACTIVE)));

            assertThatThrownBy(() -> service.submitForApproval(10L, owner))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void approvePackage_pendingApproval_becomesActive() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.PENDING_APPROVAL)));
            expectSaveEchoed();

            assertThat(service.approvePackage(10L).getStatus()).isEqualTo(PackageStatus.ACTIVE);
        }

        @Test
        void approvePackage_notPending_throwsInvalidStatusTransition() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.DRAFT)));

            assertThatThrownBy(() -> service.approvePackage(10L))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void rejectPackage_pendingApproval_revertsToDraft() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.PENDING_APPROVAL)));
            expectSaveEchoed();

            assertThat(service.rejectPackage(10L).getStatus()).isEqualTo(PackageStatus.DRAFT);
        }
    }

    @Nested
    class StatusTransitions {

        @Test
        void deactivatePackage_active_becomesInactive() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.ACTIVE)));
            expectSaveEchoed();

            assertThat(service.deactivatePackage(10L, owner).getStatus()).isEqualTo(PackageStatus.INACTIVE);
        }

        @Test
        void deactivatePackage_notActive_throwsInvalidStatusTransition() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.DRAFT)));

            assertThatThrownBy(() -> service.deactivatePackage(10L, owner))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void reactivatePackage_inactive_becomesActive() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.INACTIVE)));
            expectSaveEchoed();

            assertThat(service.reactivatePackage(10L, owner).getStatus()).isEqualTo(PackageStatus.ACTIVE);
        }

        @Test
        void archivePackage_active_becomesArchived() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.ACTIVE)));
            expectSaveEchoed();

            assertThat(service.archivePackage(10L, owner).getStatus()).isEqualTo(PackageStatus.ARCHIVED);
        }

        @Test
        void archivePackage_alreadyArchived_throwsInvalidStatusTransition() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.ARCHIVED)));

            assertThatThrownBy(() -> service.archivePackage(10L, owner))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void archivePackage_byStranger_throwsAccessDenied() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.ACTIVE)));

            assertThatThrownBy(() -> service.archivePackage(10L, stranger))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    class Queries {

        @Test
        void getPackageById_found_returnsResponse() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.ACTIVE)));

            assertThat(service.getPackageById(10L).getId()).isEqualTo(10L);
        }

        @Test
        void getPackageById_notFound_throwsResourceNotFound() {
            when(tourPackageRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPackageById(404L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getAllActivePackages_mapsActivePackagesToResponses() {
            when(tourPackageRepository.findByStatus(PackageStatus.ACTIVE))
                    .thenReturn(List.of(pkg(PackageStatus.ACTIVE)));

            List<TourPackageResponseDto> result = service.getAllActivePackages();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(PackageStatus.ACTIVE);
        }
    }
}
