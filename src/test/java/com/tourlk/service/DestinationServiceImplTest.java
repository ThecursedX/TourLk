package com.tourlk.service;

import com.tourlk.dto.ClosureSummaryDto;
import com.tourlk.dto.DestinationRequestDto;
import com.tourlk.dto.DestinationResponseDto;
import com.tourlk.entity.Destination;
import com.tourlk.enums.AccommodationStatus;
import com.tourlk.enums.DestinationStatus;
import com.tourlk.enums.PackageStatus;
import com.tourlk.enums.Province;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.DestinationInactiveException;
import com.tourlk.exception.DuplicateDestinationException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.AccommodationRepository;
import com.tourlk.repo.DestinationRepository;
import com.tourlk.repo.TourPackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import com.tourlk.util.ClosureWindow;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DestinationServiceImpl}: the name-uniqueness guard,
 * the deactivate/reactivate toggle, and the "selectable only while PUBLISHED/TEMPORARILY_CLOSED"
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

    @Mock
    private DestinationClosureBookingService closureBookingService;

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

    private static DestinationRequestDto req(String name, String description, Province province, String district,
                                             String category, String bestTime, List<String> imageUrls) {
        DestinationRequestDto dto = new DestinationRequestDto();
        dto.setName(name);
        dto.setDescription(description);
        dto.setProvince(province);
        dto.setDistrict(district);
        dto.setCategory(category);
        dto.setBestTimeToVisit(bestTime);
        dto.setImageUrls(imageUrls);
        return dto;
    }

    private DestinationRequestDto request() {
        return req("Ella", "Nine Arch Bridge and tea country",
                Province.UVA, "Badulla", "Nature", "January to March", List.of());
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
        void createDestination_uniqueName_savesAsPublished() {
            when(destinationRepository.findByNameIgnoreCase("Ella")).thenReturn(Optional.empty());
            expectSaveEchoed();

            DestinationResponseDto result = service.createDestination(request());

            assertThat(result.getStatus()).isEqualTo(DestinationStatus.PUBLISHED);
            assertThat(result.getName()).isEqualTo("Ella");
        }

        @Test
        void createDestination_savesProvinceDistrictCategoryAndKeepsLegacyRegionInStep() {
            when(destinationRepository.findByNameIgnoreCase("Ella")).thenReturn(Optional.empty());
            expectSaveEchoed();
            DestinationRequestDto request = req("Ella", null,
                    Province.UVA, "badulla", " Nature ", null,
                    List.of(" https://example.com/a.jpg ", "", "http://example.com/b.jpg"));

            DestinationResponseDto result = service.createDestination(request);

            assertThat(result.getProvince()).isEqualTo(Province.UVA);
            assertThat(result.getDistrict()).isEqualTo("Badulla");
            assertThat(result.getRegion()).isEqualTo("Uva Province");
            assertThat(result.getCategory()).isEqualTo("Nature");
            assertThat(result.getImageUrls())
                    .containsExactly("https://example.com/a.jpg", "http://example.com/b.jpg");
        }

        @Test
        void createDestination_districtNotInProvince_throwsBadRequest() {
            when(destinationRepository.findByNameIgnoreCase("Ella")).thenReturn(Optional.empty());
            DestinationRequestDto request = req("Ella", null,
                    Province.UVA, "Colombo", "Nature", null, List.of());

            assertThatThrownBy(() -> service.createDestination(request))
                    .isInstanceOf(BadRequestException.class);
            verify(destinationRepository, never()).save(any());
        }

        @Test
        void createDestination_imageUrlNotHttp_throwsBadRequest() {
            when(destinationRepository.findByNameIgnoreCase("Ella")).thenReturn(Optional.empty());
            DestinationRequestDto request = req("Ella", null,
                    Province.UVA, "Badulla", "Nature", null, List.of("ftp://example.com/a.jpg"));

            assertThatThrownBy(() -> service.createDestination(request))
                    .isInstanceOf(BadRequestException.class);
            verify(destinationRepository, never()).save(any());
        }

        @Test
        void createDestination_duplicateName_throwsDuplicateDestination() {
            when(destinationRepository.findByNameIgnoreCase("Ella"))
                    .thenReturn(Optional.of(destination(1L, DestinationStatus.PUBLISHED)));

            assertThatThrownBy(() -> service.createDestination(request()))
                    .isInstanceOf(DuplicateDestinationException.class);
            verify(destinationRepository, never()).save(any());
        }
    }

    @Nested
    class Update {

        @Test
        void updateDestination_sameEntityKeepingItsName_isAllowed() {
            when(destinationRepository.findById(1L)).thenReturn(Optional.of(destination(1L, DestinationStatus.PUBLISHED)));
            when(destinationRepository.findByNameIgnoreCase("Ella"))
                    .thenReturn(Optional.of(destination(1L, DestinationStatus.PUBLISHED)));
            expectSaveEchoed();

            assertThat(service.updateDestination(1L, request()).getRegion()).isEqualTo("Uva Province");
        }

        @Test
        void updateDestination_nameTakenByAnother_throwsDuplicateDestination() {
            when(destinationRepository.findById(1L)).thenReturn(Optional.of(destination(1L, DestinationStatus.PUBLISHED)));
            when(destinationRepository.findByNameIgnoreCase("Ella"))
                    .thenReturn(Optional.of(destination(2L, DestinationStatus.PUBLISHED)));

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
            when(destinationRepository.findById(1L)).thenReturn(Optional.of(destination(1L, DestinationStatus.PUBLISHED)));
            expectSaveEchoed();

            assertThat(service.deactivateDestination(1L).getStatus()).isEqualTo(DestinationStatus.INACTIVE);
        }

        @Test
        void reactivateDestination_marksActive() {
            when(destinationRepository.findById(1L)).thenReturn(Optional.of(destination(1L, DestinationStatus.INACTIVE)));
            expectSaveEchoed();

            assertThat(service.reactivateDestination(1L).getStatus()).isEqualTo(DestinationStatus.PUBLISHED);
        }
    }

    @Nested
    class SelectableGuard {

        @Test
        void requireSelectableDestination_active_returnsEntity() {
            when(destinationRepository.findById(1L)).thenReturn(Optional.of(destination(1L, DestinationStatus.PUBLISHED)));

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
    class ExtendedFieldsAndLifecycle {

        private DestinationRequestDto fullRequest() {
            DestinationRequestDto dto = request();
            dto.setOpeningHours("Daily 6am-6pm");
            dto.setEntryFee(new java.math.BigDecimal("1500.00"));
            dto.setVisitorRules("No drones. Modest dress.");
            dto.setLatitude(6.8667);
            dto.setLongitude(81.0466);
            return dto;
        }

        @Test
        void createDestination_persistsExtendedFields() {
            when(destinationRepository.findByNameIgnoreCase("Ella")).thenReturn(Optional.empty());
            expectSaveEchoed();

            DestinationResponseDto result = service.createDestination(fullRequest());

            assertThat(result.getOpeningHours()).isEqualTo("Daily 6am-6pm");
            assertThat(result.getEntryFee()).isEqualByComparingTo("1500.00");
            assertThat(result.getVisitorRules()).isEqualTo("No drones. Modest dress.");
            assertThat(result.getLatitude()).isEqualTo(6.8667);
            assertThat(result.getLongitude()).isEqualTo(81.0466);
        }

        @Test
        void createDestination_latitudeWithoutLongitude_throwsBadRequest() {
            when(destinationRepository.findByNameIgnoreCase("Ella")).thenReturn(Optional.empty());
            DestinationRequestDto dto = request();
            dto.setLatitude(6.8);

            assertThatThrownBy(() -> service.createDestination(dto)).isInstanceOf(BadRequestException.class);
            verify(destinationRepository, never()).save(any());
        }

        @Test
        void createDestination_saveAsDraft_createsDraft() {
            when(destinationRepository.findByNameIgnoreCase("Ella")).thenReturn(Optional.empty());
            expectSaveEchoed();
            DestinationRequestDto dto = request();
            dto.setSaveAsDraft(true);

            assertThat(service.createDestination(dto).getStatus()).isEqualTo(DestinationStatus.DRAFT);
        }

        @Test
        void submitForReview_draft_becomesPendingReview() {
            when(destinationRepository.findById(1L)).thenReturn(Optional.of(destination(1L, DestinationStatus.DRAFT)));
            expectSaveEchoed();

            assertThat(service.submitForReview(1L).getStatus()).isEqualTo(DestinationStatus.PENDING_REVIEW);
        }

        @Test
        void submitForReview_notDraft_throwsInvalidStatusTransition() {
            when(destinationRepository.findById(1L))
                    .thenReturn(Optional.of(destination(1L, DestinationStatus.PUBLISHED)));

            assertThatThrownBy(() -> service.submitForReview(1L))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void publishDestination_pendingReview_becomesPublished() {
            when(destinationRepository.findById(1L))
                    .thenReturn(Optional.of(destination(1L, DestinationStatus.PENDING_REVIEW)));
            expectSaveEchoed();

            assertThat(service.publishDestination(1L).getStatus()).isEqualTo(DestinationStatus.PUBLISHED);
        }

        @Test
        void closeTemporarily_published_storesReasonAndEndDate() {
            when(destinationRepository.findById(1L))
                    .thenReturn(Optional.of(destination(1L, DestinationStatus.PUBLISHED)));
            expectSaveEchoed();
            LocalDate until = LocalDate.now().plusDays(10);

            DestinationResponseDto result = service.closeTemporarily(1L, "  Bridge repairs ", null, until);

            assertThat(result.getStatus()).isEqualTo(DestinationStatus.TEMPORARILY_CLOSED);
            assertThat(result.getClosureReason()).isEqualTo("Bridge repairs");
            assertThat(result.getClosureUntil()).isEqualTo(until);
        }

        private DestinationResponseDto closeWith(LocalDate from, LocalDate until) {
            when(destinationRepository.findById(1L))
                    .thenReturn(Optional.of(destination(1L, DestinationStatus.PUBLISHED)));
            expectSaveEchoed();
            return service.closeTemporarily(1L, "Repairs", from, until);
        }

        @Test
        void closeTemporarily_fromOnly_isOpenEnded() {
            LocalDate from = LocalDate.now().plusDays(5);

            DestinationResponseDto result = closeWith(from, null);

            assertThat(result.getStatus()).isEqualTo(DestinationStatus.TEMPORARILY_CLOSED);
            assertThat(result.getClosureFrom()).isEqualTo(from);
            assertThat(result.getClosureUntil()).isNull();
        }

        @Test
        void closeTemporarily_untilOnly_startsToday() {
            LocalDate until = LocalDate.now().plusDays(5);

            DestinationResponseDto result = closeWith(null, until);

            assertThat(result.getClosureFrom()).isNull();
            assertThat(result.getClosureUntil()).isEqualTo(until);
            ArgumentCaptor<ClosureWindow> window = ArgumentCaptor.forClass(ClosureWindow.class);
            verify(closureBookingService).cancelAffected(eq(1L), window.capture(), eq("Repairs"));
            assertThat(window.getValue()).isEqualTo(new ClosureWindow(LocalDate.now(), until));
        }

        @Test
        void closeTemporarily_fromAndUntil_storesBoth() {
            LocalDate from = LocalDate.now().plusDays(2);
            LocalDate until = LocalDate.now().plusDays(9);

            DestinationResponseDto result = closeWith(from, until);

            assertThat(result.getClosureFrom()).isEqualTo(from);
            assertThat(result.getClosureUntil()).isEqualTo(until);
        }

        @Test
        void closeTemporarily_neitherDate_closesFromTodayUntilReopened() {
            DestinationResponseDto result = closeWith(null, null);

            assertThat(result.getStatus()).isEqualTo(DestinationStatus.TEMPORARILY_CLOSED);
            assertThat(result.getClosureFrom()).isNull();
            assertThat(result.getClosureUntil()).isNull();
            verify(closureBookingService).cancelAffected(eq(1L), eq(new ClosureWindow(LocalDate.now(), null)), eq("Repairs"));
        }

        @Test
        void closeTemporarily_returnsTheCancellationSummary() {
            ClosureSummaryDto summary = ClosureSummaryDto.builder().cancelledBookings(3).refundedCount(2).failedRefunds(1)
                    .failedBookingIds(List.of(9L)).build();
            when(closureBookingService.cancelAffected(eq(1L), any(), any())).thenReturn(summary);

            DestinationResponseDto result = closeWith(null, null);

            assertThat(result.getClosureSummary()).isSameAs(summary);
        }

        @Test
        void closeTemporarily_startInThePast_throwsBadRequestAndCancelsNothing() {
            assertThatThrownBy(() -> service.closeTemporarily(1L, "Repairs", LocalDate.now().minusDays(1), null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("start date cannot be in the past");
            verifyNoInteractions(closureBookingService);
        }

        @Test
        void closeTemporarily_startAfterEnd_throwsBadRequest() {
            assertThatThrownBy(() -> service.closeTemporarily(1L, "Repairs",
                    LocalDate.now().plusDays(5), LocalDate.now().plusDays(2)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("on or before");
            verify(destinationRepository, never()).save(any());
        }

        @Test
        void previewClosureImpact_countsWithoutChangingAnything() {
            when(destinationRepository.findById(1L))
                    .thenReturn(Optional.of(destination(1L, DestinationStatus.PUBLISHED)));
            when(closureBookingService.countAffected(eq(1L), any())).thenReturn(4);

            assertThat(service.previewClosureImpact(1L, null, LocalDate.now().plusDays(3)).getAffectedBookings())
                    .isEqualTo(4);
            verify(destinationRepository, never()).save(any());
            verify(closureBookingService, never()).cancelAffected(any(), any(), any());
        }

        @Test
        void reopenDestination_clearsClosureFrom() {
            Destination closed = destination(1L, DestinationStatus.TEMPORARILY_CLOSED);
            closed.setClosureFrom(LocalDate.now().plusDays(1));
            when(destinationRepository.findById(1L)).thenReturn(Optional.of(closed));
            expectSaveEchoed();

            assertThat(service.reopenDestination(1L).getClosureFrom()).isNull();
        }

        @Test
        void closeTemporarily_blankReason_throwsBadRequest() {
            assertThatThrownBy(() -> service.closeTemporarily(1L, "  ", null, null))
                    .isInstanceOf(BadRequestException.class);
            verify(destinationRepository, never()).save(any());
        }

        @Test
        void closeTemporarily_endDateInThePast_throwsBadRequest() {
            assertThatThrownBy(() -> service.closeTemporarily(1L, "Repairs", null, LocalDate.now().minusDays(1)))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void closeTemporarily_archived_throwsInvalidStatusTransition() {
            when(destinationRepository.findById(1L))
                    .thenReturn(Optional.of(destination(1L, DestinationStatus.ARCHIVED)));

            assertThatThrownBy(() -> service.closeTemporarily(1L, "Repairs", null, null))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void reopenDestination_closed_becomesPublishedAndClearsClosure() {
            Destination closed = destination(1L, DestinationStatus.TEMPORARILY_CLOSED);
            closed.setClosureReason("Repairs");
            closed.setClosureUntil(LocalDate.now().plusDays(3));
            when(destinationRepository.findById(1L)).thenReturn(Optional.of(closed));
            expectSaveEchoed();

            DestinationResponseDto result = service.reopenDestination(1L);

            assertThat(result.getStatus()).isEqualTo(DestinationStatus.PUBLISHED);
            assertThat(result.getClosureReason()).isNull();
            assertThat(result.getClosureUntil()).isNull();
        }

        @Test
        void reopenExpiredClosures_reopensEachExpiredDestination() {
            Destination expired = destination(1L, DestinationStatus.TEMPORARILY_CLOSED);
            expired.setClosureReason("Repairs");
            expired.setClosureUntil(LocalDate.now().minusDays(1));
            when(destinationRepository.findByStatusAndClosureUntilBefore(
                    DestinationStatus.TEMPORARILY_CLOSED, LocalDate.now())).thenReturn(List.of(expired));

            assertThat(service.reopenExpiredClosures(LocalDate.now())).isEqualTo(1);
            assertThat(expired.getStatus()).isEqualTo(DestinationStatus.PUBLISHED);
            assertThat(expired.getClosureReason()).isNull();
            verify(destinationRepository).save(expired);
        }

        @Test
        void archiveDestination_published_becomesArchived() {
            when(destinationRepository.findById(1L))
                    .thenReturn(Optional.of(destination(1L, DestinationStatus.PUBLISHED)));
            expectSaveEchoed();

            assertThat(service.archiveDestination(1L).getStatus()).isEqualTo(DestinationStatus.ARCHIVED);
        }

        @Test
        void archiveDestination_alreadyArchived_throwsInvalidStatusTransition() {
            when(destinationRepository.findById(1L))
                    .thenReturn(Optional.of(destination(1L, DestinationStatus.ARCHIVED)));

            assertThatThrownBy(() -> service.archiveDestination(1L))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void reactivateDestination_archived_becomesPublished() {
            when(destinationRepository.findById(1L))
                    .thenReturn(Optional.of(destination(1L, DestinationStatus.ARCHIVED)));
            expectSaveEchoed();

            assertThat(service.reactivateDestination(1L).getStatus()).isEqualTo(DestinationStatus.PUBLISHED);
        }

        @Test
        void getById_archivedOrDraft_isNotFoundForPublicButVisibleToAdmin() {
            for (DestinationStatus hidden : List.of(
                    DestinationStatus.ARCHIVED, DestinationStatus.DRAFT, DestinationStatus.PENDING_REVIEW)) {
                when(destinationRepository.findById(1L)).thenReturn(Optional.of(destination(1L, hidden)));

                assertThatThrownBy(() -> service.getById(1L, false))
                        .isInstanceOf(ResourceNotFoundException.class);
                assertThat(service.getById(1L, true).getStatus()).isEqualTo(hidden);
            }
        }

        @Test
        void getById_temporarilyClosed_isVisibleToPublic() {
            when(destinationRepository.findById(1L))
                    .thenReturn(Optional.of(destination(1L, DestinationStatus.TEMPORARILY_CLOSED)));

            assertThat(service.getById(1L, false).getStatus()).isEqualTo(DestinationStatus.TEMPORARILY_CLOSED);
        }

        @Test
        void requireSelectableDestination_temporarilyClosed_isAllowed_archivedIsNot() {
            when(destinationRepository.findById(1L))
                    .thenReturn(Optional.of(destination(1L, DestinationStatus.TEMPORARILY_CLOSED)));
            when(destinationRepository.findById(2L))
                    .thenReturn(Optional.of(destination(2L, DestinationStatus.ARCHIVED)));

            assertThat(service.requireSelectableDestination(1L).getId()).isEqualTo(1L);
            assertThatThrownBy(() -> service.requireSelectableDestination(2L))
                    .isInstanceOf(DestinationInactiveException.class);
        }
    }

    @Nested
    class NearbySearch {

        // From Colombo: Kandy is ~95 km away, Galle ~107 km, Ella ~131 km.
        private Destination at(Long id, String name, Double lat, Double lng) {
            Destination d = destination(id, DestinationStatus.PUBLISHED);
            d.setName(name);
            d.setLatitude(lat);
            d.setLongitude(lng);
            return d;
        }

        @Test
        void searchNearby_returnsDestinationsWithinRadius_nearestFirst_withDistance() {
            when(destinationRepository.findByStatusIn(any())).thenReturn(List.of(
                    at(1L, "Galle", 6.0329, 80.2170),
                    at(2L, "Kandy", 7.2906, 80.6337),
                    at(3L, "Ella", 6.8667, 81.0466),
                    at(4L, "Unmapped", null, null)));

            List<DestinationResponseDto> result = service.searchNearby("6.9271,79.8612", 120.0);

            assertThat(result).extracting(DestinationResponseDto::getName).containsExactly("Kandy", "Galle");
            assertThat(result.get(0).getDistanceKm()).isBetween(90.0, 100.0);
            assertThat(result.get(1).getDistanceKm()).isBetween(100.0, 115.0);
        }

        @Test
        void searchNearby_defaultsRadiusTo50Km() {
            when(destinationRepository.findByStatusIn(any())).thenReturn(List.of(at(2L, "Kandy", 7.2906, 80.6337)));

            assertThat(service.searchNearby("6.9271,79.8612", null)).isEmpty();
        }

        @Test
        void searchNearby_samePoint_isZeroKm() {
            when(destinationRepository.findByStatusIn(any())).thenReturn(List.of(at(1L, "Galle", 6.0329, 80.2170)));

            assertThat(service.searchNearby("6.0329,80.2170", 1.0).get(0).getDistanceKm()).isZero();
        }

        @Test
        void searchNearby_malformedOrOutOfRangePoint_throwsBadRequest() {
            for (String bad : List.of("abc", "6.9", "6.9,abc", "91,80", "6.9,181", ",")) {
                assertThatThrownBy(() -> service.searchNearby(bad, 10.0))
                        .as(bad).isInstanceOf(BadRequestException.class);
            }
        }

        @Test
        void searchNearby_nonPositiveOrHugeRadius_throwsBadRequest() {
            assertThatThrownBy(() -> service.searchNearby("6.9,79.8", 0.0)).isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> service.searchNearby("6.9,79.8", -5.0)).isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> service.searchNearby("6.9,79.8", 50_000.0)).isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    class Queries {

        @Test
        void getAllActive_returnsPublicDestinations_withoutCounts() {
            when(destinationRepository.findByStatusIn(any()))
                    .thenReturn(List.of(destination(1L, DestinationStatus.PUBLISHED)));

            List<DestinationResponseDto> result = service.getAllActive();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getActivePackageCount()).isNull();
        }

        @Test
        void getById_populatesCounts() {
            when(destinationRepository.findById(1L)).thenReturn(Optional.of(destination(1L, DestinationStatus.PUBLISHED)));
            when(tourPackageRepository.countByDestinationIdAndStatus(1L, PackageStatus.ACTIVE)).thenReturn(3L);
            when(accommodationRepository.countByLocationIdAndStatus(1L, AccommodationStatus.ACTIVE)).thenReturn(2L);

            DestinationResponseDto result = service.getById(1L, true);

            assertThat(result.getActivePackageCount()).isEqualTo(3L);
            assertThat(result.getActiveAccommodationCount()).isEqualTo(2L);
        }

        @Test
        void getByProvince_matchesStoredProvinceAndLegacyRegion_activeOnly() {
            Destination stored = destination(1L, DestinationStatus.PUBLISHED);
            stored.setProvince(Province.UVA);
            // Legacy row: no province column value, only the old region text.
            Destination legacy = destination(2L, DestinationStatus.PUBLISHED);
            Destination otherProvince = destination(3L, DestinationStatus.PUBLISHED);
            otherProvince.setProvince(Province.SOUTHERN);
            otherProvince.setRegion("Southern Province");
            Destination inactive = destination(4L, DestinationStatus.INACTIVE);
            inactive.setProvince(Province.UVA);
            when(destinationRepository.findAll()).thenReturn(List.of(stored, legacy, otherProvince, inactive));

            List<DestinationResponseDto> result = service.getByProvince(Province.UVA);

            assertThat(result).extracting(DestinationResponseDto::getId).containsExactly(1L, 2L);
        }

        @Test
        void getCategorySuggestions_mergesStarterListAndUsedCategories_caseInsensitively() {
            when(destinationRepository.findDistinctCategories()).thenReturn(List.of("beach", "Surfing", " "));

            List<String> result = service.getCategorySuggestions();

            assertThat(result).contains("Beach", "Surfing", "Wildlife");
            assertThat(result).doesNotContain("beach", " ");
            assertThat(result).doesNotHaveDuplicates();
        }

        @Test
        void searchByName_filtersOutInactive() {
            when(destinationRepository.findByNameContainingIgnoreCase("ell"))
                    .thenReturn(List.of(
                            destination(1L, DestinationStatus.PUBLISHED),
                            destination(2L, DestinationStatus.INACTIVE)));

            assertThat(service.searchByName("ell")).hasSize(1);
        }
    }
}
