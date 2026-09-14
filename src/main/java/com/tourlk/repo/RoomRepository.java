package com.tourlk.repo;

import com.tourlk.entity.Room;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByAccommodationId(Long accommodationId);

    /**
     * Fetches a room with a pessimistic write lock, held for the rest of
     * the caller's transaction. Used by {@code RoomReservationServiceImpl}
     * to serialize availability checks for that room — see the comment on
     * {@code RoomReservationServiceImpl#getLockedRoom} for why the room
     * row is the thing we lock, not the overlap-count query (same
     * phantom-row reasoning as {@code TourPackageRepository#findByIdForUpdate}
     * in the Booking module). Bounded to 5s so a stuck transaction fails
     * fast instead of hanging every other reservation attempt for the
     * same room.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT r FROM Room r WHERE r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);

}
