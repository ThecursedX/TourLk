package com.tourlk.service;

import com.tourlk.config.JpaAuditingConfig;
import com.tourlk.dto.RoomReservationRequestDto;
import com.tourlk.entity.Accommodation;
import com.tourlk.entity.Destination;
import com.tourlk.entity.Room;
import com.tourlk.entity.RoomReservation;
import com.tourlk.entity.User;
import com.tourlk.enums.AccommodationStatus;
import com.tourlk.enums.Role;
import com.tourlk.enums.RoomReservationStatus;
import com.tourlk.exception.RoomUnavailableException;
import com.tourlk.repo.AccommodationRepository;
import com.tourlk.repo.RoomRepository;
import com.tourlk.repo.RoomReservationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Room inventory against a real (H2) database: the overlap query's half-open
 * date semantics, inventory being released on cancellation, the automatic
 * FULLY_BOOKED status, and — the point of the pessimistic room lock —
 * concurrent confirmations never overbooking a room type.
 * <p>
 * Not wrapped in a test transaction: each service call must commit in its own
 * transaction for the threads to see (and block on) each other's work.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:room_inventory;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MSSQLServer")
@Import({JpaAuditingConfig.class, RoomReservationServiceImpl.class, AccommodationServiceImpl.class})
class RoomInventoryIntegrationTest {

    @Autowired
    private RoomReservationServiceImpl reservationService;
    @Autowired
    private RoomReservationRepository reservationRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private AccommodationRepository accommodationRepository;
    @Autowired
    private jakarta.persistence.EntityManager em;

    @MockBean
    private DestinationService destinationService;
    @MockBean
    private NotificationService notificationService;

    private User owner;
    private User tourist;
    private Accommodation accommodation;
    private Room room;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        cleanUp();
        owner = persist(User.builder().name("Holly").email("holly@inv.test").password("x")
                .role(Role.HOTEL_PARTNER).build());
        tourist = persist(User.builder().name("Tess").email("tess@inv.test").password("x")
                .role(Role.TOURIST).build());
        Destination galle = persist(Destination.builder().name("Galle").build());
        accommodation = accommodationRepository.save(Accommodation.builder()
                .name("Ocean View").description("d").location(galle).owner(owner)
                .status(AccommodationStatus.ACTIVE).build());
        room = roomRepository.save(Room.builder()
                .accommodation(accommodation).roomType("Deluxe")
                .pricePerNight(new BigDecimal("80.00")).totalRooms(3).maxOccupancy(2).build());
    }

    @AfterEach
    void cleanUp() {
        reservationRepository.deleteAll();
        roomRepository.deleteAll();
        accommodationRepository.deleteAll();
        deleteAllOf("Destination");
        deleteAllOf("User");
    }

    // ------------------------------------------------------------------
    // overlap semantics (repository query)
    // ------------------------------------------------------------------

    @Test
    void overlapQuery_countsOnlyConfirmedReservationsOverlappingTheRequestedRange() {
        LocalDate in = today.plusDays(10);
        stored(RoomReservationStatus.CONFIRMED, in, in.plusDays(3), 2);
        stored(RoomReservationStatus.PENDING, in, in.plusDays(3), 1);
        stored(RoomReservationStatus.CANCELLED, in, in.plusDays(3), 1);
        stored(RoomReservationStatus.COMPLETED, in, in.plusDays(3), 1);

        // Partial overlap at either end, full containment, and exact match all hit the 2 confirmed rooms.
        assertThat(sum(in.plusDays(2), in.plusDays(5))).isEqualTo(2);
        assertThat(sum(in.minusDays(2), in.plusDays(1))).isEqualTo(2);
        assertThat(sum(in.plusDays(1), in.plusDays(2))).isEqualTo(2);
        assertThat(sum(in, in.plusDays(3))).isEqualTo(2);
    }

    @Test
    void overlapQuery_backToBackStaysDoNotCollide_checkoutDayIsCheckinDay() {
        LocalDate in = today.plusDays(10);
        stored(RoomReservationStatus.CONFIRMED, in, in.plusDays(3), 3);

        assertThat(sum(in.plusDays(3), in.plusDays(5))).isZero();   // checks in on the other's check-out day
        assertThat(sum(in.minusDays(2), in)).isZero();              // checks out on the other's check-in day
    }

    @Test
    void overlapQuery_isScopedToTheRoomType() {
        Room other = roomRepository.save(Room.builder()
                .accommodation(accommodation).roomType("Suite")
                .pricePerNight(new BigDecimal("150.00")).totalRooms(1).maxOccupancy(4).build());
        LocalDate in = today.plusDays(10);
        stored(RoomReservationStatus.CONFIRMED, in, in.plusDays(3), 3);

        assertThat(reservationRepository.sumReservedRoomsOverlapping(
                other.getId(), java.util.EnumSet.of(RoomReservationStatus.CONFIRMED), in, in.plusDays(3), 0L)).isZero();
    }

    // ------------------------------------------------------------------
    // booking / cancellation inventory flow (service + repository)
    // ------------------------------------------------------------------

    @Test
    void confirm_overlappingDates_cannotExceedTotalRooms() {
        LocalDate in = today.plusDays(10);
        Long first = pending(in, in.plusDays(4), 2).getId();

        // The unpaid PENDING first reservation already holds 2 of the 3 rooms, so a second overlapping
        // request for 2 is rejected up front.
        assertThatThrownBy(() -> reservationService.createReservation(
                new RoomReservationRequestDto(room.getId(), in.plusDays(2), in.plusDays(6), 2), tourist))
                .isInstanceOf(RoomUnavailableException.class);

        // Confirming the held reservation must not clash with itself.
        reservationService.confirmReservation(first, owner);
        assertThat(reservationRepository.findById(first).orElseThrow().getStatus())
                .isEqualTo(RoomReservationStatus.CONFIRMED);
    }

    @Test
    void confirm_nonOverlappingAndBackToBackDates_bothFitInTheSameRooms() {
        LocalDate in = today.plusDays(10);
        Long first = pending(in, in.plusDays(3), 3).getId();
        Long backToBack = pending(in.plusDays(3), in.plusDays(5), 3).getId();

        reservationService.confirmReservation(first, owner);
        reservationService.confirmReservation(backToBack, owner);

        assertThat(sum(in, in.plusDays(5))).isEqualTo(6);   // 3 + 3, never more than 3 on any one night
    }

    @Test
    void cancelConfirmedReservation_releasesInventoryForAnotherGuest() {
        LocalDate in = today.plusDays(10);
        Long first = pending(in, in.plusDays(3), 3).getId();
        reservationService.confirmReservation(first, owner);
        RoomReservationRequestDto another = new RoomReservationRequestDto(room.getId(), in, in.plusDays(3), 1);

        assertThatThrownBy(() -> reservationService.createReservation(another, tourist))
                .isInstanceOf(RoomUnavailableException.class);

        reservationService.cancelReservation(first, tourist);
        Long second = reservationService.createReservation(another, tourist).getId();
        reservationService.confirmReservation(second, owner);

        assertThat(sum(in, in.plusDays(3))).isEqualTo(1);
    }

    @Test
    void createReservation_whenAllRoomsAreTaken_isRejectedUpFront() {
        LocalDate in = today.plusDays(10);
        reservationService.confirmReservation(pending(in, in.plusDays(3), 3).getId(), owner);

        assertThatThrownBy(() -> reservationService.createReservation(
                new RoomReservationRequestDto(room.getId(), in.plusDays(1), in.plusDays(2), 1), tourist))
                .isInstanceOf(RoomUnavailableException.class);
    }

    // ------------------------------------------------------------------
    // FULLY_BOOKED auto status
    // ------------------------------------------------------------------

    @Test
    void accommodation_becomesFullyBookedWhenLastRoomIsTakenTonight_andReopensOnCancellation() {
        Long id = pending(today, today.plusDays(2), 3).getId();

        reservationService.confirmReservation(id, owner);
        assertThat(statusOfAccommodation()).isEqualTo(AccommodationStatus.FULLY_BOOKED);

        reservationService.cancelReservation(id, owner);
        assertThat(statusOfAccommodation()).isEqualTo(AccommodationStatus.ACTIVE);
    }

    @Test
    void accommodation_staysActiveWhenOnlyFutureNightsAreFull() {
        LocalDate in = today.plusDays(5);
        reservationService.confirmReservation(pending(in, in.plusDays(2), 3).getId(), owner);

        assertThat(statusOfAccommodation()).isEqualTo(AccommodationStatus.ACTIVE);
    }

    @Test
    void accommodation_staysActiveWhileAnotherRoomTypeStillHasAvailability() {
        roomRepository.save(Room.builder()
                .accommodation(accommodation).roomType("Suite")
                .pricePerNight(new BigDecimal("150.00")).totalRooms(1).maxOccupancy(4).build());

        reservationService.confirmReservation(pending(today, today.plusDays(2), 3).getId(), owner);

        assertThat(statusOfAccommodation()).isEqualTo(AccommodationStatus.ACTIVE);
    }

    // ------------------------------------------------------------------
    // concurrency
    // ------------------------------------------------------------------

    @Test
    void concurrentReservations_ofOverlappingDates_neverOverbookTheRoomType() throws Exception {
        int attempts = 8;                                  // 8 guests race for 3 rooms
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            ids.add((long) i);
        }
        List<Long> created = java.util.Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger confirmed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<Void>> futures = new ArrayList<>();
        try {
            for (Long id : ids) {
                Callable<Void> task = () -> {
                    start.await();
                    try {
                        created.add(reservationService.createReservation(
                                new RoomReservationRequestDto(room.getId(), today, today.plusDays(2), 1), tourist)
                                .getId());
                        confirmed.incrementAndGet();
                    } catch (RoomUnavailableException e) {
                        rejected.incrementAndGet();
                    }
                    return null;
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            for (Future<Void> future : futures) {
                future.get(60, TimeUnit.SECONDS);   // surfaces any unexpected exception (e.g. lock timeout)
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(confirmed.get()).isEqualTo(3);
        assertThat(rejected.get()).isEqualTo(attempts - 3);
        created.forEach(id -> reservationService.confirmReservation(id, owner));   // holds confirm without clashing
        assertThat(sum(today, today.plusDays(2))).isEqualTo(3);
        assertThat(statusOfAccommodation()).isEqualTo(AccommodationStatus.FULLY_BOOKED);
    }

    @Test
    void concurrentConfirmations_ofDisjointDates_allSucceed() throws Exception {
        int attempts = 4;
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            LocalDate in = today.plusDays(10 + i * 3L);
            ids.add(pending(in, in.plusDays(3), 3).getId());   // back-to-back, each takes every room
        }

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        try {
            for (Long id : ids) {
                Callable<Void> task = () -> {
                    start.await();
                    reservationService.confirmReservation(id, owner);
                    return null;
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            for (Future<Void> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(reservationRepository.findAll())
                .allMatch(r -> r.getStatus() == RoomReservationStatus.CONFIRMED);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private RoomReservation pending(LocalDate in, LocalDate out, int rooms) {
        return stored(RoomReservationStatus.PENDING, in, out, rooms);
    }

    private RoomReservation stored(RoomReservationStatus status, LocalDate in, LocalDate out, int rooms) {
        return reservationRepository.save(RoomReservation.builder()
                .tourist(tourist).room(room).checkInDate(in).checkOutDate(out)
                .numberOfRooms(rooms).status(status).build());
    }

    private int sum(LocalDate in, LocalDate out) {
        return reservationRepository.sumReservedRoomsOverlapping(
                room.getId(), java.util.EnumSet.of(RoomReservationStatus.CONFIRMED), in, out, 0L);
    }

    private AccommodationStatus statusOfAccommodation() {
        return accommodationRepository.findById(accommodation.getId()).orElseThrow().getStatus();
    }

    private <T> T persist(T entity) {
        return txTemplate().execute(status -> {
            em.persist(entity);
            return entity;
        });
    }

    private void deleteAllOf(String entityName) {
        txTemplate().executeWithoutResult(status -> em.createQuery("DELETE FROM " + entityName).executeUpdate());
    }

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private org.springframework.transaction.support.TransactionTemplate txTemplate() {
        return new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

}
