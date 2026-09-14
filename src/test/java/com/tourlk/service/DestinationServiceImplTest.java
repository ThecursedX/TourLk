package com.tourlk.service;

import com.tourlk.dto.DestinationRequestDto;
import com.tourlk.dto.DestinationResponseDto;
import com.tourlk.entity.Destination;
import com.tourlk.enums.AccommodationStatus;
import com.tourlk.enums.DestinationStatus;
import com.tourlk.enums.PackageStatus;
import com.tourlk.exception.DestinationInactiveException;
import com.tourlk.exception.DuplicateDestinationException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.AccommodationRepository;
import com.tourlk.repo.DestinationRepository;
import com.tourlk.repo.TourPackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * Unit tests for {@link DestinationServiceImpl}: the name-uniqueness guard,
 * the deactivate/reactivate toggle, and the "selectable only while ACTIVE"
 * rule the Tour Package / Accommodation modules rely on. Repositories mocked.
 */
@ExtendWith(MockitoExtension.class)
class DestinationServiceImplTest {

    @Mock
    private DestinationRepository destinationRepository;
    @Mock
    private TourPackageRepository tourPackageRepository;
    @Mock
    private AccommodationRepository accommodationRepository;

    @InjectMocks
    private DestinationServiceImpl service;

    @BeforeEach
    void setUp() {
        // toResponse(withCounts=true) queries both count repos.
        lenient().when(tourPackageRepository.countByDestinationIdAndStatus(any(), any())).thenReturn(0L);
        lenient().when(accommodationRepository.countByLocationIdAndStatus(any(), any())).thenReturn(0L);
    }

    private Destination destination(Long id, DestinationStatus status) {
        return Destination.builder()
                .id(id).name("Ella").region("Uva Province").status(status)
                .build();
    }

    private DestinationRequestDto request() {
        return new DestinationRequestDto("Ella", "Uva Province", "Nine Arch Bridge and tea country");
    }

    private void expectSaveEchoed() {
        when(destinationRepository.save(any(Destination.class))).thenAnswer(inv -> {
            Destination d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(1L);
            }
            return d;
        });
    }

    @Nested
    class Create {

        @Test
        void createDestination_uniqueName_savesAsActive() {
            when(destinationRepository.findByNameIgnoreCase("Ella")).thenReturn(Optional.empty());
            expectSaveEchoed();

            DestinationResponseDto result = service.createDestination(request());

            assertThat(result.getStatus()).isEqualTo(DestinationStatus.ACTIVE);
            assertThat(result.getName()).isEqualTo("Ella");
        }

        @Test
        void createDestination_duplicateName_throwsDuplicateDestination() {
            when(destinationRepository.findByNameIgnoreCase("Ella"))
                    .thenReturn(Optional.of(destination(1L, DestinationStatus.ACTIVE)));

            assertThatThrownBy(() -> service.createDestination(request()))
                    .isInstanceOf(DuplicateDestinationException.class);
            verify(destinationRepository, never()).save(any());
        }
    }

    @Nested
    class Update {

        @Test
        void updateDestination_sameEntityKeepingItsName_isAllowed() {
            when(destinationRepository.findById(1L)).thenReturn(Optional.of(destination(1L, DestinationStatus.ACTIVE)));
            when(destinationRepository.findByNameIgnoreCase("Ella"))
                    .thenReturn(Optional.of(destination(1L, DestinationStatus.ACTIVE)));
            expectSaveEchoed();

            assertThat(service.updateDestination(1L, request()).getRegion()).isEqualTo("Uva Province");
        }

        @Test
        void updateDestination_nameTakenByAnother_throwsDuplicateDestination() {
            when(destinationRepository.findById(1L)).thenReturn(Optional.of(destination(1L, DestinationStatus.ACTIVE)));
            when(destinationRepository.findByNameIgnoreCase("Ella"))
                    .thenReturn(Optional.of(destination(2L, DestinationStatus.ACTIVE)));

            assertThatThrownBy(() -> service.updateDestination(1L, request()))
                    .isInstanceOf(DuplicateDestinationException.class);
        }

        @Test
        void updateDestination_notFound_throwsResourceNotFound() {
            when(destinationRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateDestination(404L, request()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class ActivationToggle {

        @Test
        void deactivateDestination_marksInactive() {
            when(destinationRepository.findById(1L)).thenReturn(Optional.of(destination(1L, DestinationStatus.ACTIVE)));
            expectSaveEchoed();

            assertThat(service.deactivateDestination(1L).getStatus()).isEqualTo(DestinationStatus.INACTIVE);
        }

        @Test
        void reactivateDestination_marksActive() {
            when(destinationRepository.findById(1L)).thenReturn(Optional.of(destination(1L, DestinationStatus.INACTIVE)));
            expectSaveEchoed();

            assertThat(service.reactivateDestination(1L).getStatus()).isEqualTo(DestinationStatus.ACTIVE);
        }
    }

    @Nested
    class SelectableGuard {

        @Test
        void requireSelectableDestination_active_returnsEntity() {
            when(destinationRepository.findById(1L)).thenReturn(Optional.of(destination(1L, DestinationStatus.ACTIVE)));

            assertThat(service.requireSelectableDestination(1L).getId()).isEqualTo(1L);
        }

        @Test
        void requireSelectableDestination_inactive_throwsDestinationInactive() {
            when(destinationRepository.findById(1L)).thenReturn(Optional.of(destination(1L, DestinationStatus.INACTIVE)));

            assertThatThrownBy(() -> service.requireSelectableDestination(1L))
                    .isInstanceOf(DestinationInactiveException.class);
        }

        @Test
        void requireSelectableDestination_missing_throwsResourceNotFound() {
            when(destinationRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.requireSelectableDestination(404L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Queries {

        @Test
        void getAllActive_returnsOnlyActive_withoutCounts() {
            when(destinationRepository.findByStatus(DestinationStatus.ACTIVE))
                    .thenReturn(List.of(destination(1L, DestinationStatus.ACTIVE)));

            List<DestinationResponseDto> result = service.getAllActive();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getActivePackageCount()).isNull();
        }

        @Test
        void getById_populatesCounts() {
            when(destinationRepository.findById(1L)).thenReturn(Optional.of(destination(1L, DestinationStatus.ACTIVE)));
            when(tourPackageRepository.countByDestinationIdAndStatus(1L, PackageStatus.ACTIVE)).thenReturn(3L);
            when(accommodationRepository.countByLocationIdAndStatus(1L, AccommodationStatus.ACTIVE)).thenReturn(2L);

            DestinationResponseDto result = service.getById(1L);

            assertThat(result.getActivePackageCount()).isEqualTo(3L);
            assertThat(result.getActiveAccommodationCount()).isEqualTo(2L);
        }

        @Test
        void searchByName_filtersOutInactive() {
            when(destinationRepository.findByNameContainingIgnoreCase("ell"))
                    .thenReturn(List.of(
                            destination(1L, DestinationStatus.ACTIVE),
                            destination(2L, DestinationStatus.INACTIVE)));

            assertThat(service.searchByName("ell")).hasSize(1);
        }
    }
}
