package com.tourlk.repo;

import com.tourlk.config.JpaAuditingConfig;
import com.tourlk.entity.Accommodation;
import com.tourlk.entity.Destination;
import com.tourlk.entity.Room;
import com.tourlk.entity.User;
import com.tourlk.enums.AccommodationStatus;
import com.tourlk.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link AccommodationRepository#search} against H2: the service tests mock the repository, so this is
 * what actually proves the name / stars / price-range / status filtering in the JPQL.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class AccommodationSearchRepositoryTest {

    private static final Set<AccommodationStatus> BROWSABLE = EnumSet.of(
            AccommodationStatus.ACTIVE, AccommodationStatus.FULLY_BOOKED, AccommodationStatus.TEMPORARILY_UNAVAILABLE);

    @Autowired
    private TestEntityManager em;
    @Autowired
    private AccommodationRepository repository;

    private User owner;
    private Destination galle;
    private Destination kandy;

    @BeforeEach
    void setUp() {
        owner = em.persist(User.builder().name("Holly").email("holly@search.test").password("x")
                .role(Role.HOTEL_PARTNER).build());
        galle = em.persist(Destination.builder().name("Galle").build());
        kandy = em.persist(Destination.builder().name("Kandy").build());

        // name, location, stars, status, room prices
        stay("Ocean View", galle, 4, AccommodationStatus.ACTIVE, "40", "120");
        stay("Hill Lodge", kandy, 3, AccommodationStatus.FULLY_BOOKED, "80");
        stay("50% Off Inn", galle, 2, AccommodationStatus.TEMPORARILY_UNAVAILABLE, "25");
        stay("Snake_Pit Hostel", kandy, null, AccommodationStatus.ACTIVE, "10");
        stay("Brackets [Annex]", galle, 5, AccommodationStatus.ACTIVE, "300");
        stay("No Rooms Yet", galle, 4, AccommodationStatus.ACTIVE);
        // hidden listings that match everything else
        stay("Ocean Draft", galle, 5, AccommodationStatus.DRAFT, "40");
        stay("Ocean Pending", galle, 5, AccommodationStatus.PENDING_APPROVAL, "40");
        stay("Ocean Inactive", galle, 5, AccommodationStatus.INACTIVE, "40");
        stay("Ocean Archived", galle, 5, AccommodationStatus.ARCHIVED, "40");
        em.flush();
        em.clear();
    }

    private void stay(String name, Destination location, Integer stars, AccommodationStatus status, String... prices) {
        Accommodation acc = em.persist(Accommodation.builder().name(name).description("d").location(location)
                .starRating(stars).status(status).owner(owner).build());
        int i = 0;
        for (String price : prices) {
            em.persist(Room.builder().accommodation(acc).roomType("Type " + (i++))
                    .pricePerNight(new BigDecimal(price)).totalRooms(1).maxOccupancy(2).build());
        }
    }

    private List<String> search(Long locationId, String namePattern, Integer minStars,
                                String minPrice, String maxPrice) {
        return repository.search(BROWSABLE, locationId, namePattern, minStars,
                        minPrice == null ? null : new BigDecimal(minPrice),
                        maxPrice == null ? null : new BigDecimal(maxPrice))
                .stream().map(Accommodation::getName).toList();
    }

    @Test
    void noFilters_returnsEveryBrowsableListingAndNoHiddenOne() {
        assertThat(search(null, null, null, null, null)).containsExactlyInAnyOrder(
                "Ocean View", "Hill Lodge", "50% Off Inn", "Snake_Pit Hostel", "Brackets [Annex]", "No Rooms Yet");
    }

    @Test
    void location_narrowsTheResults() {
        assertThat(search(kandy.getId(), null, null, null, null))
                .containsExactlyInAnyOrder("Hill Lodge", "Snake_Pit Hostel");
    }

    @Test
    void nameContains_isCaseInsensitive() {
        assertThat(search(null, "%ocean%", null, null, null)).containsExactly("Ocean View");
        assertThat(search(null, "%LODGE%".toLowerCase(), null, null, null)).containsExactly("Hill Lodge");
    }

    @Test
    void percentAndUnderscore_areMatchedLiterally() {
        assertThat(search(null, "%!%%", null, null, null)).containsExactly("50% Off Inn");
        assertThat(search(null, "%snake!_pit%", null, null, null)).containsExactly("Snake_Pit Hostel");
        // an unescaped wildcard char would have matched much more; an escaped one with no literal match finds nothing
        assertThat(search(null, "%hill!_lodge%", null, null, null)).isEmpty();
        assertThat(search(null, "%![annex]%", null, null, null)).containsExactly("Brackets [Annex]");
    }

    @Test
    void nameSearch_neverLeaksHiddenListings() {
        assertThat(search(null, "%ocean%", null, null, null)).doesNotContain(
                "Ocean Draft", "Ocean Pending", "Ocean Inactive", "Ocean Archived");
    }

    @Test
    void minStars_isInclusive_andExcludesUnratedListings() {
        assertThat(search(null, null, 4, null, null))
                .containsExactlyInAnyOrder("Ocean View", "Brackets [Annex]", "No Rooms Yet");
        assertThat(search(null, null, 1, null, null)).doesNotContain("Snake_Pit Hostel");
        assertThat(search(null, null, 5, null, null)).containsExactly("Brackets [Annex]");
    }

    @Test
    void priceRange_matchesWhenAnyRoomTypeIsInsideIt_inclusive() {
        // Ocean View has rooms at 40 and 120; only the 40 is inside 30..50.
        assertThat(search(null, null, null, "30", "50")).containsExactly("Ocean View");
        assertThat(search(null, null, null, "80", "80")).containsExactly("Hill Lodge");
        assertThat(search(null, null, null, "100", "130")).containsExactly("Ocean View");
    }

    @Test
    void priceRange_oneSidedBounds() {
        assertThat(search(null, null, null, "100", null))
                .containsExactlyInAnyOrder("Ocean View", "Brackets [Annex]");
        assertThat(search(null, null, null, null, "25"))
                .containsExactlyInAnyOrder("50% Off Inn", "Snake_Pit Hostel");
    }

    @Test
    void priceRange_excludesListingsWithoutRooms() {
        assertThat(search(null, null, null, "0", null)).doesNotContain("No Rooms Yet");
        assertThat(search(null, null, null, null, "1000")).doesNotContain("No Rooms Yet");
    }

    @Test
    void combinedFilters_allApply_andHiddenStatusesNeverAppear() {
        assertThat(search(galle.getId(), "%o%", 4, "30", "60")).containsExactly("Ocean View");
        assertThat(search(galle.getId(), null, 5, "0", "100")).isEmpty(); // the only browsable 5-star costs 300
    }

    @Test
    void hiddenStatuses_neverAppearUnderAnyFilterCombination() {
        Set<String> hidden = Set.of("Ocean Draft", "Ocean Pending", "Ocean Inactive", "Ocean Archived");
        Long[] locations = {null, galle.getId()};
        String[] patterns = {null, "%ocean%"};
        Integer[] stars = {null, 1, 5};
        String[][] prices = {{null, null}, {"0", null}, {null, "100"}, {"30", "50"}};

        for (Long location : locations) {
            for (String pattern : patterns) {
                for (Integer star : stars) {
                    for (String[] range : prices) {
                        assertThat(search(location, pattern, star, range[0], range[1])).doesNotContainAnyElementsOf(hidden);
                    }
                }
            }
        }
    }
}
