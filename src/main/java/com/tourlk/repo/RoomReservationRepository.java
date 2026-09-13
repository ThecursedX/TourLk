package com.tourlk.repo;

import com.tourlk.entity.RoomReservation;
import com.tourlk.enums.RoomReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface RoomReservationRepository extends JpaRepository<RoomReservation, Long> {

    List<RoomReservation> findByTouristId(Long touristId);

    List<RoomReservation> findByRoomId(Long roomId);

    /**
     * Sums numberOfRooms across CONFIRMED reservations of a room whose
     * stay overlaps [checkInDate, checkOutDate). Two ranges overlap iff
     * {@code existing.checkIn < requested.checkOut AND existing.checkOut > requested.checkIn}
     * — this is a half-open-interval comparison, so a checkout on day N
     * does NOT collide with a check-in on day N (the room turns over that
     * day), matching normal hotel semantics.
     * <p>
     * Deliberately NOT annotated with {@code @Lock} — same reasoning as
     * {@code BookingRepository#sumTravelersByPackageAndDateAndStatusIn}:
     * a locked aggregate only locks the rows it matches, and there may be
     * zero matching rows for a brand-new date range, so two concurrent
     * transactions could both read 0 and both proceed. Safe to run
     * unlocked here because the caller ({@code RoomReservationServiceImpl})
     * always holds a pessimistic write lock on the parent {@code Room}
     * row first, which serializes every availability check/reservation
     * for that room into one at a time.
     */
    @Query("SELECT COALESCE(SUM(rr.numberOfRooms), 0) FROM RoomReservation rr "
            + "WHERE rr.room.id = :roomId AND rr.status = :status "
            + "AND rr.checkInDate < :checkOutDate AND rr.checkOutDate > :checkInDate")
    int sumReservedRoomsOverlapping(@Param("roomId") Long roomId,
                                     @Param("status") RoomReservationStatus status,
                                     @Param("checkInDate") LocalDate checkInDate,
                                     @Param("checkOutDate") LocalDate checkOutDate);

}
