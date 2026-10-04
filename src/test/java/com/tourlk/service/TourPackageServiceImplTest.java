package com.tourlk.service;

import com.tourlk.config.BudgetTierProperties;
import com.tourlk.dto.ItineraryDayRequestDto;
import com.tourlk.dto.PackageSearchCriteria;
import com.tourlk.dto.TourPackageRequestDto;
import com.tourlk.dto.TourPackageResponseDto;
import com.tourlk.entity.Destination;
import com.tourlk.entity.ItineraryDay;
import com.tourlk.entity.TourPackage;
import com.tourlk.entity.User;
import com.tourlk.enums.BookingStatus;
import com.tourlk.enums.BudgetTier;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.DestinationStatus;
import com.tourlk.enums.PackageSort;
import com.tourlk.enums.PackageStatus;
import com.tourlk.enums.ReviewStatus;
import com.tourlk.enums.ReviewableType;
import com.tourlk.enums.Role;
import com.tourlk.enums.VerificationStatus;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.ChangesRequireConfirmationException;
import com.tourlk.exception.DestinationInactiveException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.LicenceNotVerifiedException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.BookingRepository;
import com.tourlk.repo.ItineraryDayRepository;
import com.tourlk.repo.PackageDepartureRepository;
import com.tourlk.repo.ReviewRepository;
import com.tourlk.repo.ReviewRepository.RatingAggregate;
import com.tourlk.repo.TourPackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @Mock
    private NotificationService notificationService;
    @Mock
    private ItineraryDayRepository itineraryDayRepository;
    @Mock
    private PackageDepartureRepository departureRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private com.tourlk.repo.PackageAddOnRepository packageAddOnRepository;
    /** Real policy (75 / 200 USD per day) — it's pure logic, nothing to mock. */
    @Spy
    private BudgetTierPolicy budgetTierPolicy =
            new BudgetTierPolicy(new BudgetTierProperties(new BigDecimal("75"), new BigDecimal("200")));

    @InjectMocks
    private TourPackageServiceImpl service;

    private User owner;
    private User stranger;
    private User admin;
    private Destination ella;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).name("Gina Guide").email("gina@example.com").role(Role.GUIDE)
                .verificationStatus(VerificationStatus.VERIFIED).build();
        stranger = User.builder().id(2L).name("Other Guide").role(Role.GUIDE)
                .verificationStatus(VerificationStatus.VERIFIED).build();
        admin = User.builder().id(9L).name("Amy Admin").role(Role.ADMIN).build();
        ella = Destination.builder()
                .id(1L).name("Ella").region("Uva Province").status(DestinationStatus.PUBLISHED)
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
        return new TourPackageRequestDto("Hill Country", "Tea trails", 1L, 3, new BigDecimal("150.00"), 12,
                List.of(), List.of(), List.of(), List.of());
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

        @Test
        void createPackage_byUnverifiedGuide_throwsLicenceNotVerified() {
            owner.setVerificationStatus(VerificationStatus.PENDING);

            assertThatThrownBy(() -> service.createPackage(request(), owner))
                    .isInstanceOf(LicenceNotVerifiedException.class);
            verify(tourPackageRepository, never()).save(any());
        }

        @Test
        void createPackage_byGuideWithExpiredLicence_throwsLicenceNotVerified() {
            owner.setVerificationStatus(VerificationStatus.VERIFIED);
            owner.setLicenceExpiry(java.time.LocalDate.now().minusDays(1));

            assertThatThrownBy(() -> service.createPackage(request(), owner))
                    .isInstanceOf(LicenceNotVerifiedException.class)
                    .hasMessage("Your licence expired on " + owner.getLicenceExpiry() + ". Renew it on your profile.");
            verify(tourPackageRepository, never()).save(any());
        }

        @Test
        void createPackage_byGuideExpiringToday_isStillAllowed() {
            owner.setVerificationStatus(VerificationStatus.VERIFIED);
            owner.setLicenceExpiry(java.time.LocalDate.now());
            expectSaveEchoed();

            assertThat(service.createPackage(request(), owner).getStatus()).isEqualTo(PackageStatus.DRAFT);
        }

        @Test
        void createPackage_byAdmin_skipsLicenceCheck() {
            expectSaveEchoed();

            assertThat(service.createPackage(request(), admin).getStatus()).isEqualTo(PackageStatus.DRAFT);
        }
    }

    @Nested
    class UpdatePackage {

        @Test
        void updatePackage_draftByOwner_appliesChanges() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.DRAFT)));
            expectSaveEchoed();

            TourPackageRequestDto edit = new TourPackageRequestDto(
                    "New Title", "New desc", 1L, 4, new BigDecimal("200.00"), 20,
                    List.of(), List.of(), List.of(), List.of());
            TourPackageResponseDto result = service.updatePackage(10L, edit, owner, false);

            assertThat(result.getTitle()).isEqualTo("New Title");
            assertThat(result.getMaxCapacity()).isEqualTo(20);
        }

        @Test
        void updatePackage_activeByAdmin_isAllowed() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.ACTIVE)));
            expectSaveEchoed();

            assertThat(service.updatePackage(10L, request(), admin, false).getStatus()).isEqualTo(PackageStatus.ACTIVE);
        }

        @Test
        void updatePackage_byUnrelatedGuide_throwsAccessDenied() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.DRAFT)));

            assertThatThrownBy(() -> service.updatePackage(10L, request(), stranger, false))
                    .isInstanceOf(AccessDeniedException.class);
            verify(tourPackageRepository, never()).save(any());
        }

        @Test
        void updatePackage_whilePendingApproval_throwsInvalidStatusTransition() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.PENDING_APPROVAL)));

            assertThatThrownBy(() -> service.updatePackage(10L, request(), owner, false))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void updatePackage_notFound_throwsResourceNotFound() {
            when(tourPackageRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updatePackage(404L, request(), owner, false))
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
        void submitForApproval_byUnverifiedGuide_throwsLicenceNotVerified() {
            owner.setVerificationStatus(VerificationStatus.REJECTED);

            assertThatThrownBy(() -> service.submitForApproval(10L, owner))
                    .isInstanceOf(LicenceNotVerifiedException.class);
            verify(tourPackageRepository, never()).save(any());
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

            assertThat(service.rejectPackage(10L, "Needs photos").getStatus()).isEqualTo(PackageStatus.DRAFT);
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

            assertThat(service.getPackageById(10L, null).getId()).isEqualTo(10L);
        }

        @Test
        void getPackageById_notFound_throwsResourceNotFound() {
            when(tourPackageRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPackageById(404L, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void browsePackages_noCriteria_mapsActivePackagesToResponses() {
            when(tourPackageRepository.findAll(any(Specification.class), any(Sort.class)))
                    .thenReturn(List.of(pkg(PackageStatus.ACTIVE)));

            List<TourPackageResponseDto> result = service.browsePackages(PackageSearchCriteria.builder().build());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(PackageStatus.ACTIVE);
        }
    }

    @Nested
    class ItineraryAndLists {

        private TourPackageRequestDto requestWithItinerary(List<ItineraryDayRequestDto> days) {
            return new TourPackageRequestDto("Hill Country", "Tea trails", 1L, 3, new BigDecimal("150.00"), 12,
                    days, List.of(), List.of(), List.of());
        }

        @Test
        void createPackage_withValidItinerary_savesDaysAndReturnsThem() {
            expectSaveEchoed();
            List<ItineraryDayRequestDto> days = List.of(
                    new ItineraryDayRequestDto(1, "Arrival", "Settle in", List.of("Hotel")),
                    new ItineraryDayRequestDto(2, "Hike", null, List.of("Ella Rock", "Nine Arches")));
            when(itineraryDayRepository.findByTourPackageIdInOrderByDayNumberAsc(List.of(10L))).thenReturn(
                    days.stream().map(d -> ItineraryDay.builder()
                            .id(1L).tourPackage(pkg(PackageStatus.DRAFT)).dayNumber(d.getDayNumber())
                            .title(d.getTitle()).description(d.getDescription())
                            .placesToVisit(d.getPlacesToVisit()).build())
                            .toList());

            TourPackageResponseDto result = service.createPackage(requestWithItinerary(days), owner);

            verify(itineraryDayRepository).saveAll(any());
            assertThat(result.getItineraryDays()).hasSize(2);
        }

        @Test
        void createPackage_duplicateDayNumbers_throwsBadRequest() {
            List<ItineraryDayRequestDto> days = List.of(
                    new ItineraryDayRequestDto(1, "Arrival", null, List.of()),
                    new ItineraryDayRequestDto(1, "Also day 1", null, List.of()));

            assertThatThrownBy(() -> service.createPackage(requestWithItinerary(days), owner))
                    .isInstanceOf(BadRequestException.class);
            verify(tourPackageRepository, never()).save(any());
        }

        @Test
        void createPackage_dayNumberBeyondDuration_throwsBadRequest() {
            // durationDays is 3 in requestWithItinerary
            List<ItineraryDayRequestDto> days = List.of(new ItineraryDayRequestDto(4, "Too far", null, List.of()));

            assertThatThrownBy(() -> service.createPackage(requestWithItinerary(days), owner))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void createPackage_dayNumberZero_throwsBadRequest() {
            List<ItineraryDayRequestDto> days = List.of(new ItineraryDayRequestDto(0, "Invalid", null, List.of()));

            assertThatThrownBy(() -> service.createPackage(requestWithItinerary(days), owner))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void updatePackage_replacesItinerary_deletesThenReinserts() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.DRAFT)));
            expectSaveEchoed();
            List<ItineraryDayRequestDto> days = List.of(new ItineraryDayRequestDto(1, "Day one", null, List.of()));

            service.updatePackage(10L, requestWithItinerary(days), owner, false);

            verify(itineraryDayRepository).deleteByTourPackageId(10L);
            verify(itineraryDayRepository).saveAll(any());
        }

        @Test
        void createPackage_imageUrlWithoutHttpPrefix_throwsBadRequest() {
            TourPackageRequestDto request = new TourPackageRequestDto(
                    "Hill Country", "Tea trails", 1L, 3, new BigDecimal("150.00"), 12,
                    List.of(), List.of(), List.of(), List.of("not-a-url"));

            assertThatThrownBy(() -> service.createPackage(request, owner))
                    .isInstanceOf(BadRequestException.class);
            verify(tourPackageRepository, never()).save(any());
        }

        @Test
        void createPackage_tooManyImages_throwsBadRequest() {
            List<String> elevenImages = IntStream.range(0, 11)
                    .mapToObj(i -> "https://example.com/" + i + ".jpg")
                    .toList();
            TourPackageRequestDto request = new TourPackageRequestDto(
                    "Hill Country", "Tea trails", 1L, 3, new BigDecimal("150.00"), 12,
                    List.of(), List.of(), List.of(), elevenImages);

            assertThatThrownBy(() -> service.createPackage(request, owner))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void createPackage_blankInclusionsAndExclusionsAreDropped() {
            expectSaveEchoed();
            TourPackageRequestDto request = new TourPackageRequestDto(
                    "Hill Country", "Tea trails", 1L, 3, new BigDecimal("150.00"), 12,
                    List.of(), Arrays.asList("Breakfast", "  ", null), List.of("Flights"), List.of());

            TourPackageResponseDto result = service.createPackage(request, owner);

            assertThat(result.getInclusions()).containsExactly("Breakfast");
            assertThat(result.getExclusions()).containsExactly("Flights");
        }
    }

    @Nested
    class Browse {

        private TourPackage active(long id, String price, int days) {
            return TourPackage.builder()
                    .id(id).title("Package " + id).description("d").destination(ella)
                    .durationDays(days).price(new BigDecimal(price)).maxCapacity(10)
                    .status(PackageStatus.ACTIVE).createdBy(owner)
                    .build();
        }

        private RatingAggregate rating(long packageId, double average, long count) {
            return new RatingAggregate() {
                public Long getReviewableId() {
                    return packageId;
                }

                public Double getAverageRating() {
                    return average;
                }

                public Long getReviewCount() {
                    return count;
                }
            };
        }

        private void stubResults(TourPackage... packages) {
            when(tourPackageRepository.findAll(any(Specification.class), any(Sort.class)))
                    .thenReturn(List.of(packages));
        }

        @Test
        void listMapping_usesOneBatchedQueryEachForItineraryDeparturesAndRatings() {
            stubResults(active(1, "100", 2), active(2, "100", 2), active(3, "100", 2));
            when(reviewRepository.aggregateRatings(eq(ReviewableType.TOUR_PACKAGE), anyCollection(), anyCollection()))
                    .thenReturn(List.of(rating(2, 4.25, 4)));
            when(departureRepository.findPackageIdsWithDepartures(anyCollection())).thenReturn(List.of(3L));

            List<TourPackageResponseDto> result = service.browsePackages(PackageSearchCriteria.builder().build());

            verify(reviewRepository, times(1)).aggregateRatings(ReviewableType.TOUR_PACKAGE, List.of(1L, 2L, 3L), ReviewStatus.HIDDEN_FROM_PUBLIC);
            verify(itineraryDayRepository, times(1)).findByTourPackageIdInOrderByDayNumberAsc(List.of(1L, 2L, 3L));
            verify(departureRepository, times(1)).findPackageIdsWithDepartures(List.of(1L, 2L, 3L));
            verify(departureRepository, never()).existsByTourPackageId(any());

            assertThat(result.get(0).getReviewCount()).isZero();
            assertThat(result.get(0).getAverageRating()).isZero();
            assertThat(result.get(1).getAverageRating()).isEqualTo(4.3); // rounded like the review summary
            assertThat(result.get(1).getReviewCount()).isEqualTo(4);
            assertThat(result).extracting(TourPackageResponseDto::isHasDepartures).containsExactly(false, false, true);
        }

        @Test
        void responses_carryBudgetTierFromPricePerDay() {
            // 74.99/day, exactly 75/day, 199.99/day, exactly 200/day
            stubResults(active(1, "149.98", 2), active(2, "150.00", 2), active(3, "399.98", 2), active(4, "400", 2));

            List<TourPackageResponseDto> result = service.browsePackages(PackageSearchCriteria.builder().build());

            assertThat(result).extracting(TourPackageResponseDto::getBudgetTier).containsExactly(
                    BudgetTier.BUDGET, BudgetTier.STANDARD, BudgetTier.STANDARD, BudgetTier.LUXURY);
        }

        @Test
        void ratingSort_highestAverageFirst_thenMostReviews_unratedLast() {
            stubResults(active(1, "100", 2), active(2, "100", 2), active(3, "100", 2), active(4, "100", 2));
            when(reviewRepository.aggregateRatings(eq(ReviewableType.TOUR_PACKAGE), anyCollection(), anyCollection()))
                    .thenReturn(List.of(rating(2, 4.0, 3), rating(3, 4.8, 1), rating(4, 4.0, 10)));

            List<TourPackageResponseDto> result = service.browsePackages(
                    PackageSearchCriteria.builder().sort(PackageSort.RATING).build());

            assertThat(result).extracting(TourPackageResponseDto::getId).containsExactly(3L, 4L, 2L, 1L);
        }

        @Test
        void databaseSorts_arePassedToTheRepository() {
            ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
            when(tourPackageRepository.findAll(any(Specification.class), sort.capture())).thenReturn(List.of());

            service.browsePackages(PackageSearchCriteria.builder().sort(PackageSort.PRICE_DESC).build());
            service.browsePackages(PackageSearchCriteria.builder().sort(PackageSort.NEWEST).build());
            service.browsePackages(PackageSearchCriteria.builder().sort(PackageSort.RATING).build());

            assertThat(sort.getAllValues().get(0).getOrderFor("price").isDescending()).isTrue();
            assertThat(sort.getAllValues().get(1).getOrderFor("createdAt").isDescending()).isTrue();
            assertThat(sort.getAllValues().get(2).isUnsorted()).isTrue();
        }

        @Test
        void minPriceAboveMaxPrice_throwsBadRequest() {
            PackageSearchCriteria criteria = PackageSearchCriteria.builder()
                    .minPrice(new BigDecimal("500")).maxPrice(new BigDecimal("100")).build();

            assertThatThrownBy(() -> service.browsePackages(criteria)).isInstanceOf(BadRequestException.class);
            verify(tourPackageRepository, never()).findAll(any(Specification.class), any(Sort.class));
        }

        @Test
        void minDaysAboveMaxDays_throwsBadRequest() {
            PackageSearchCriteria criteria = PackageSearchCriteria.builder().minDays(7).maxDays(3).build();

            assertThatThrownBy(() -> service.browsePackages(criteria)).isInstanceOf(BadRequestException.class);
        }

        @Test
        void nonPositiveDaysOrNegativePrice_throwsBadRequest() {
            assertThatThrownBy(() -> service.browsePackages(PackageSearchCriteria.builder().minDays(0).build()))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> service.browsePackages(
                    PackageSearchCriteria.builder().minPrice(new BigDecimal("-1")).build()))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void overlongSearchText_throwsBadRequest() {
            PackageSearchCriteria criteria = PackageSearchCriteria.builder().q("x".repeat(101)).build();

            assertThatThrownBy(() -> service.browsePackages(criteria)).isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    class Visibility {

        @Test
        void activePackage_visibleToAnonymous() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.ACTIVE)));

            assertThat(service.getPackageById(10L, null).getId()).isEqualTo(10L);
        }

        @Test
        void nonActivePackage_hiddenFromAnonymousAndOtherUsers_as404() {
            for (PackageStatus status : List.of(PackageStatus.DRAFT, PackageStatus.PENDING_APPROVAL,
                    PackageStatus.INACTIVE, PackageStatus.ARCHIVED)) {
                when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(status)));

                assertThatThrownBy(() -> service.getPackageById(10L, null))
                        .isInstanceOf(ResourceNotFoundException.class)
                        .hasMessage("Tour package not found with id: 10");
                assertThatThrownBy(() -> service.getPackageById(10L, stranger))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }

        @Test
        void nonActivePackage_visibleToOwnerAndAdmin() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.DRAFT)));

            assertThat(service.getPackageById(10L, owner).getStatus()).isEqualTo(PackageStatus.DRAFT);
            assertThat(service.getPackageById(10L, admin).getStatus()).isEqualTo(PackageStatus.DRAFT);
        }

        @Test
        void adminListing_withoutStatus_returnsAllNewestFirst() {
            when(tourPackageRepository.findAll(any(Sort.class)))
                    .thenReturn(List.of(pkg(PackageStatus.ARCHIVED)));

            assertThat(service.getAllPackagesForAdmin(null)).hasSize(1);

            ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
            verify(tourPackageRepository).findAll(sort.capture());
            assertThat(sort.getValue().getOrderFor("createdAt").isDescending()).isTrue();
        }

        @Test
        void adminListing_withStatus_filtersByStatus() {
            when(tourPackageRepository.findByStatus(eq(PackageStatus.INACTIVE), any(Sort.class)))
                    .thenReturn(List.of(pkg(PackageStatus.INACTIVE)));

            assertThat(service.getAllPackagesForAdmin(PackageStatus.INACTIVE))
                    .extracting(TourPackageResponseDto::getStatus).containsExactly(PackageStatus.INACTIVE);
        }
    }

    @Nested
    class Rejection {

        @Test
        void reject_storesTrimmedReason_andNotifiesOwnerWithIt() {
            TourPackage pending = pkg(PackageStatus.PENDING_APPROVAL);
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pending));
            expectSaveEchoed();

            TourPackageResponseDto result = service.rejectPackage(10L, "  Please add real photos  ");

            assertThat(pending.getRejectionReason()).isEqualTo("Please add real photos");
            assertThat(result.getRejectionReason()).isEqualTo("Please add real photos");
            verify(notificationService).notify(eq(owner), eq(NotificationType.PACKAGE_REJECTED), any(),
                    contains("Please add real photos"), eq("/packages/10/edit"));
        }

        @Test
        void reject_blankReason_throwsBadRequest() {
            assertThatThrownBy(() -> service.rejectPackage(10L, "   "))
                    .isInstanceOf(BadRequestException.class);
            verify(tourPackageRepository, never()).save(any());
        }

        @Test
        void resubmit_clearsRejectionReason() {
            TourPackage rejected = pkg(PackageStatus.DRAFT);
            rejected.setRejectionReason("Please add real photos");
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(rejected));
            expectSaveEchoed();

            TourPackageResponseDto result = service.submitForApproval(10L, owner);

            assertThat(rejected.getRejectionReason()).isNull();
            assertThat(result.getRejectionReason()).isNull();
        }
    }

    @Nested
    class EditConfirmation {

        private TourPackageRequestDto edit(String price, int days, int capacity) {
            return new TourPackageRequestDto("Hill Country", "Tea trails", 1L, days, new BigDecimal(price), capacity,
                    List.of(), List.of(), List.of(), List.of());
        }

        @Test
        void activeWithUpcomingBookings_priceChange_requiresConfirmation() {
            TourPackage active = pkg(PackageStatus.ACTIVE); // 3 days, 150.00, capacity 12
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(active));
            when(bookingRepository.countUpcomingActiveBookings(10L, LocalDate.now(), BookingStatus.TERMINAL))
                    .thenReturn(2L);

            assertThatThrownBy(() -> service.updatePackage(10L, edit("180.00", 4, 10), owner, false))
                    .isInstanceOfSatisfying(ChangesRequireConfirmationException.class, ex -> {
                        assertThat(ex.getChangedFields()).containsExactly("price", "durationDays", "maxCapacity");
                        assertThat(ex.getAffectedBookings()).isEqualTo(2);
                    });
            verify(tourPackageRepository, never()).save(any());
            assertThat(active.getPrice()).isEqualByComparingTo("150.00"); // untouched
        }

        @Test
        void activeWithUpcomingBookings_confirmed_saves_withoutCountingBookings() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.ACTIVE)));
            expectSaveEchoed();

            TourPackageResponseDto result = service.updatePackage(10L, edit("180.00", 3, 12), owner, true);

            assertThat(result.getPrice()).isEqualByComparingTo("180.00");
            verify(bookingRepository, never()).countUpcomingActiveBookings(anyLong(), any(), any());
        }

        @Test
        void activeWithoutUpcomingBookings_priceChange_savesWithoutConfirmation() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.ACTIVE)));
            when(bookingRepository.countUpcomingActiveBookings(eq(10L), any(), any())).thenReturn(0L);
            expectSaveEchoed();

            assertThat(service.updatePackage(10L, edit("180.00", 3, 12), owner, false).getPrice())
                    .isEqualByComparingTo("180.00");
        }

        @Test
        void samePriceWrittenDifferently_isNotAChange() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.ACTIVE)));
            expectSaveEchoed();

            service.updatePackage(10L, edit("150", 3, 12), owner, false); // 150 vs 150.00

            verify(bookingRepository, never()).countUpcomingActiveBookings(anyLong(), any(), any());
        }

        @Test
        void draftPackage_neverRequiresConfirmation() {
            when(tourPackageRepository.findById(10L)).thenReturn(Optional.of(pkg(PackageStatus.DRAFT)));
            expectSaveEchoed();

            service.updatePackage(10L, edit("999.00", 9, 99), owner, false);

            verify(bookingRepository, never()).countUpcomingActiveBookings(anyLong(), any(), any());
        }
    }
}
