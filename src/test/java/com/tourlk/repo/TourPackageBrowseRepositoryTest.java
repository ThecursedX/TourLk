package com.tourlk.repo;

import com.tourlk.config.JpaAuditingConfig;
import com.tourlk.entity.Destination;
import com.tourlk.entity.ItineraryDay;
import com.tourlk.entity.PackageDeparture;
import com.tourlk.entity.Review;
import com.tourlk.entity.TourPackage;
import com.tourlk.entity.User;
import com.tourlk.enums.BudgetTier;
import com.tourlk.enums.PackageStatus;
import com.tourlk.enums.ReviewStatus;
import com.tourlk.enums.ReviewableType;
import com.tourlk.enums.Role;
import com.tourlk.repo.ReviewRepository.RatingAggregate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link TourPackageSpecifications} and the batched listing queries
 * against H2, since the service tests mock the repositories.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class TourPackageBrowseRepositoryTest {

    private static final BigDecimal STANDARD_MIN = new BigDecimal("75");
    private static final BigDecimal LUXURY_MIN = new BigDecimal("200");

    @Autowired
    private TestEntityManager em;
    @Autowired
    private TourPackageRepository tourPackageRepository;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private PackageDepartureRepository departureRepository;
    @Autowired
    private ItineraryDayRepository itineraryDayRepository;

    private User guide;
    private User tourist;
    private Destination kandy;
    private Destination galle;

    // budget:   "Tea Trails" 3 days, 150 (50/day), Kandy
    // standard: "Coastal 100% Fun" 5 days, 500 (100/day), Galle
    // luxury:   "Royal Hill Escape" 2 days, 600 (300/day), Kandy
    private TourPackage budget;
    private TourPackage standard;
    private TourPackage luxury;

    @BeforeEach
    void setUp() {
        guide = em.persist(User.builder().name("Gina").email("gina@example.com").password("x").role(Role.GUIDE).build());
        tourist = em.persist(User.builder().name("Tess").email("tess@example.com").password("x").role(Role.TOURIST)
                .build());
        kandy = em.persist(Destination.builder().name("Kandy").build());
        galle = em.persist(Destination.builder().name("Galle").build());

        budget = em.persist(pkg("Tea Trails", "Walk the estates of the hill country", kandy, 3, "150"));
        standard = em.persist(pkg("Coastal 100% Fun", "Forts, beaches and whale watching", galle, 5, "500"));
        luxury = em.persist(pkg("Royal Hill Escape", "Private villa with TEA tasting", kandy, 2, "600"));
        em.persist(TourPackage.builder()
                .title("Draft Tea Tour").description("tea").destination(kandy).durationDays(1)
                .price(new BigDecimal("10")).maxCapacity(5).status(PackageStatus.DRAFT).createdBy(guide).build());
        em.flush();
    }

    private TourPackage pkg(String title, String description, Destination destination, int days, String price) {
        return TourPackage.builder()
                .title(title).description(description).destination(destination).durationDays(days)
                .price(new BigDecimal(price)).maxCapacity(10).status(PackageStatus.ACTIVE).createdBy(guide)
                .build();
    }

    private List<String> titles(Specification<TourPackage> filter) {
        return titles(filter, Sort.by("title"));
    }

    private List<String> titles(Specification<TourPackage> filter, Sort sort) {
        Specification<TourPackage> spec =
                Specification.where(TourPackageSpecifications.hasStatus(PackageStatus.ACTIVE)).and(filter);
        return tourPackageRepository.findAll(spec, sort).stream().map(TourPackage::getTitle).toList();
    }

    private void departure(TourPackage p, LocalDate date) {
        em.persist(PackageDeparture.builder().tourPackage(p).departureDate(date).seatsTotal(10).build());
    }

    private void review(TourPackage p, int rating, long sourceBookingId) {
        em.persist(Review.builder().reviewer(tourist).reviewableType(ReviewableType.TOUR_PACKAGE)
                .reviewableId(p.getId()).sourceBookingId(sourceBookingId).rating(rating).build());
    }

    @Test
    void noFilters_returnsOnlyActivePackages() {
        assertThat(titles(null)).containsExactly("Coastal 100% Fun", "Royal Hill Escape", "Tea Trails");
    }

    @Test
    void textContains_matchesTitleOrDescription_caseInsensitive() {
        assertThat(titles(TourPackageSpecifications.textContains("  tea "))).containsExactly(
                "Royal Hill Escape", "Tea Trails"); // description "TEA tasting", title "Tea Trails"
        assertThat(titles(TourPackageSpecifications.textContains("WHALE"))).containsExactly("Coastal 100% Fun");
    }

    @Test
    void textContains_treatsLikeWildcardsLiterally() {
        assertThat(titles(TourPackageSpecifications.textContains("100%"))).containsExactly("Coastal 100% Fun");
        assertThat(titles(TourPackageSpecifications.textContains("%"))).containsExactly("Coastal 100% Fun");
        assertThat(titles(TourPackageSpecifications.textContains("_"))).isEmpty();
    }

    @Test
    void destinationPriceAndDurationRanges_combine() {
        assertThat(titles(TourPackageSpecifications.inDestination(kandy.getId())))
                .containsExactly("Royal Hill Escape", "Tea Trails");
        assertThat(titles(TourPackageSpecifications.priceBetween(new BigDecimal("150"), new BigDecimal("500"))))
                .containsExactly("Coastal 100% Fun", "Tea Trails");
        assertThat(titles(TourPackageSpecifications.durationBetween(3, null)))
                .containsExactly("Coastal 100% Fun", "Tea Trails");
        assertThat(titles(TourPackageSpecifications.durationBetween(null, 2))).containsExactly("Royal Hill Escape");
        assertThat(titles(Specification.where(TourPackageSpecifications.inDestination(kandy.getId()))
                .and(TourPackageSpecifications.durationBetween(3, 3)))).containsExactly("Tea Trails");
    }

    @Test
    void budgetTier_filtersByPricePerDay() {
        assertThat(titles(TourPackageSpecifications.inBudgetTier(BudgetTier.BUDGET, STANDARD_MIN, LUXURY_MIN)))
                .containsExactly("Tea Trails");
        assertThat(titles(TourPackageSpecifications.inBudgetTier(BudgetTier.STANDARD, STANDARD_MIN, LUXURY_MIN)))
                .containsExactly("Coastal 100% Fun");
        assertThat(titles(TourPackageSpecifications.inBudgetTier(BudgetTier.LUXURY, STANDARD_MIN, LUXURY_MIN)))
                .containsExactly("Royal Hill Escape");
    }

    @Test
    void budgetTier_boundaryPricePerDay_isTheHigherTier() {
        em.persist(pkg("Exactly Standard", "d", kandy, 4, "300")); // 75/day
        em.flush();

        assertThat(titles(TourPackageSpecifications.inBudgetTier(BudgetTier.STANDARD, STANDARD_MIN, LUXURY_MIN)))
                .containsExactly("Coastal 100% Fun", "Exactly Standard");
    }

    @Test
    void travelDate_keepsPackagesDepartingOnOrAfter_andPackagesWithoutDepartures() {
        LocalDate travelDate = LocalDate.now().plusDays(30);
        departure(standard, travelDate);                // exactly on the date -> kept
        departure(luxury, travelDate.minusDays(1));     // only before the date -> dropped
        // budget has no departures -> filter doesn't apply, kept
        em.flush();

        assertThat(titles(TourPackageSpecifications.departsOnOrAfter(travelDate)))
                .containsExactly("Coastal 100% Fun", "Tea Trails");
    }

    @Test
    void sorts_priceDurationAndNewest() {
        assertThat(titles(null, Sort.by(Sort.Order.asc("price")))).containsExactly(
                "Tea Trails", "Coastal 100% Fun", "Royal Hill Escape");
        assertThat(titles(null, Sort.by(Sort.Order.desc("price")))).containsExactly(
                "Royal Hill Escape", "Coastal 100% Fun", "Tea Trails");
        assertThat(titles(null, Sort.by(Sort.Order.asc("durationDays")))).containsExactly(
                "Royal Hill Escape", "Tea Trails", "Coastal 100% Fun");
        assertThat(titles(null, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))).containsExactly(
                "Royal Hill Escape", "Coastal 100% Fun", "Tea Trails");
    }

    @Test
    void aggregateRatings_averagesAndCountsPerPackage_inOneQuery() {
        review(budget, 5, 1);
        review(budget, 4, 2);
        review(standard, 3, 3);
        em.persist(Review.builder().reviewer(tourist).reviewableType(ReviewableType.VEHICLE)
                .reviewableId(budget.getId()).sourceBookingId(4L).rating(1).build()); // same id, other type
        em.flush();

        Map<Long, RatingAggregate> ratings = reviewRepository
                .aggregateRatings(ReviewableType.TOUR_PACKAGE, List.of(budget.getId(), standard.getId(), luxury.getId()),
                        ReviewStatus.HIDDEN_FROM_PUBLIC)
                .stream().collect(Collectors.toMap(RatingAggregate::getReviewableId, Function.identity()));

        assertThat(ratings).containsOnlyKeys(budget.getId(), standard.getId());
        assertThat(ratings.get(budget.getId()).getAverageRating()).isEqualTo(4.5);
        assertThat(ratings.get(budget.getId()).getReviewCount()).isEqualTo(2);
        assertThat(ratings.get(standard.getId()).getAverageRating()).isEqualTo(3.0);
    }

    @Test
    void batchLookups_forDeparturesAndItinerary() {
        departure(luxury, LocalDate.now().plusDays(3));
        departure(luxury, LocalDate.now().plusDays(9));
        em.persist(ItineraryDay.builder().tourPackage(budget).dayNumber(2).title("Day two").build());
        em.persist(ItineraryDay.builder().tourPackage(budget).dayNumber(1).title("Day one").build());
        em.persist(ItineraryDay.builder().tourPackage(standard).dayNumber(1).title("Arrive").build());
        em.flush();
        List<Long> ids = List.of(budget.getId(), standard.getId(), luxury.getId());

        assertThat(departureRepository.findPackageIdsWithDepartures(ids)).containsExactly(luxury.getId());
        assertThat(itineraryDayRepository.findByTourPackageIdInOrderByDayNumberAsc(List.of(budget.getId())))
                .extracting(ItineraryDay::getTitle).containsExactly("Day one", "Day two");
        assertThat(itineraryDayRepository.findByTourPackageIdInOrderByDayNumberAsc(ids)).hasSize(3);
    }
}
