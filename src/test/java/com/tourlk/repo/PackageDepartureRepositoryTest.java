package com.tourlk.repo;

import com.tourlk.config.JpaAuditingConfig;
import com.tourlk.entity.Booking;
import com.tourlk.entity.Destination;
import com.tourlk.entity.PackageDeparture;
import com.tourlk.entity.TourPackage;
import com.tourlk.entity.User;
import com.tourlk.enums.BookingStatus;
import com.tourlk.enums.PackageStatus;
import com.tourlk.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the departure queries plus {@link BookingRepository#countActiveBookingsOnDate}
 * and {@link BookingRepository#countUpcomingActiveBookings} against H2, since
 * the service tests mock them.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class PackageDepartureRepositoryTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private PackageDepartureRepository departureRepository;
    @Autowired
    private BookingRepository bookingRepository;

    private User tourist;
    private TourPackage tourPackage;
    private TourPackage otherPackage;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        User guide = em.persist(User.builder()
                .name("Gina").email("gina@example.com").password("x").role(Role.GUIDE).build());
        tourist = em.persist(User.builder()
                .name("Tess").email("tess@example.com").password("x").role(Role.TOURIST).build());
        Destination kandy = em.persist(Destination.builder().name("Kandy").build());
        tourPackage = em.persist(pkg("Hill Country", kandy, guide));
        otherPackage = em.persist(pkg("Coast", kandy, guide));
        date = LocalDate.now().plusDays(30);
    }

    private TourPackage pkg(String title, Destination destination, User owner) {
        return TourPackage.builder()
                .title(title).description("d").destination(destination).durationDays(3)
                .price(new BigDecimal("100.00")).maxCapacity(10).status(PackageStatus.ACTIVE).createdBy(owner)
                .build();
    }

    private void departure(TourPackage p, LocalDate d) {
        em.persist(PackageDeparture.builder().tourPackage(p).departureDate(d).seatsTotal(10).build());
    }

    private Booking booking(TourPackage p, LocalDate travelDate, BookingStatus status) {
        return em.persist(Booking.builder()
                .tourist(tourist).tourPackage(p).travelDate(travelDate).numberOfTravelers(2).status(status)
                .build());
    }

    @Test
    void upcomingDepartures_excludeTodayAndPast_areOrderedAndScopedToPackage() {
        departure(tourPackage, date.plusDays(5));
        departure(tourPackage, date);
        departure(tourPackage, LocalDate.now());
        departure(tourPackage, LocalDate.now().minusDays(3));
        departure(otherPackage, date);

        assertThat(departureRepository.findByTourPackageIdAndDepartureDateAfterOrderByDepartureDateAsc(
                tourPackage.getId(), LocalDate.now()))
                .extracting(PackageDeparture::getDepartureDate)
                .containsExactly(date, date.plusDays(5));
    }

    @Test
    void existsQueries_reflectPackageAndDate() {
        departure(tourPackage, date);

        assertThat(departureRepository.existsByTourPackageId(tourPackage.getId())).isTrue();
        assertThat(departureRepository.existsByTourPackageId(otherPackage.getId())).isFalse();
        assertThat(departureRepository.existsByTourPackageIdAndDepartureDate(tourPackage.getId(), date)).isTrue();
        assertThat(departureRepository.findByTourPackageIdAndDepartureDate(tourPackage.getId(), date.plusDays(1)))
                .isEmpty();
    }

    @Test
    void sameDateTwiceOnOnePackage_violatesUniqueConstraint() {
        departure(tourPackage, date);

        assertThatThrownBy(() -> departureRepository.saveAndFlush(PackageDeparture.builder()
                .tourPackage(tourPackage).departureDate(date).seatsTotal(5).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void countActiveBookingsOnDate_countsNonTerminalBookingsAndPendingRescheduleTargets() {
        booking(tourPackage, date, BookingStatus.PENDING);
        booking(tourPackage, date, BookingStatus.CONFIRMED);
        booking(tourPackage, date, BookingStatus.RESCHEDULED);
        booking(tourPackage, date, BookingStatus.CANCELLED);
        booking(tourPackage, date, BookingStatus.COMPLETED);
        booking(tourPackage, date.plusDays(1), BookingStatus.CONFIRMED);   // other date
        booking(otherPackage, date, BookingStatus.CONFIRMED);              // other package

        Booking movingHere = booking(tourPackage, date.plusDays(2), BookingStatus.RESCHEDULE_REQUESTED);
        movingHere.setRequestedTravelDate(date);
        Booking cancelledWhileMoving = booking(tourPackage, date.plusDays(3), BookingStatus.CANCELLED);
        cancelledWhileMoving.setRequestedTravelDate(date);
        em.flush();

        // PENDING + CONFIRMED + RESCHEDULED + the pending reschedule onto this date
        assertThat(bookingRepository.countActiveBookingsOnDate(tourPackage.getId(), date, BookingStatus.TERMINAL))
                .isEqualTo(4);
        assertThat(bookingRepository.countActiveBookingsOnDate(
                tourPackage.getId(), date.plusDays(10), BookingStatus.TERMINAL)).isZero();
    }

    @Test
    void countUpcomingActiveBookings_countsNonTerminalFromDateOnward() {
        LocalDate today = LocalDate.now();
        booking(tourPackage, today, BookingStatus.CONFIRMED);                 // today counts
        booking(tourPackage, today.plusDays(5), BookingStatus.PENDING);
        booking(tourPackage, today.plusDays(9), BookingStatus.RESCHEDULE_REQUESTED);
        booking(tourPackage, today.minusDays(1), BookingStatus.CONFIRMED);    // past
        booking(tourPackage, today.plusDays(5), BookingStatus.CANCELLED);     // terminal
        booking(tourPackage, today.plusDays(5), BookingStatus.COMPLETED);     // terminal
        booking(otherPackage, today.plusDays(5), BookingStatus.CONFIRMED);    // other package
        em.flush();

        assertThat(bookingRepository.countUpcomingActiveBookings(tourPackage.getId(), today, BookingStatus.TERMINAL))
                .isEqualTo(3);
    }
}
