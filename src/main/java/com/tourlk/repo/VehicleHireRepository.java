package com.tourlk.repo;

import com.tourlk.entity.VehicleHire;
import com.tourlk.enums.VehicleHireStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface VehicleHireRepository extends JpaRepository<VehicleHire, Long> {

    List<VehicleHire> findByTouristId(Long touristId);

    List<VehicleHire> findByVehicleId(Long vehicleId);

    /**
     * Whether a CONFIRMED hire of this vehicle already overlaps
     * [startDate, endDate]. A vehicle is a single unit (unlike a Room
     * type's totalRooms), so this is existence, not a sum: any overlap
     * at all makes the vehicle unavailable for the requested range.
     * Overlap uses closed intervals ({@code existing.startDate <= requested.endDate
     * AND existing.endDate >= requested.startDate}) rather than the
     * half-open comparison Accommodation used for check-in/check-out —
     * a vehicle hire occupies each day of its range inclusively (no same-day
     * turnover the way a hotel room has), so touching endpoints DO overlap.
     * <p>
     * Deliberately NOT annotated with {@code @Lock} — same reasoning as
     * {@code RoomReservationRepository#sumReservedRoomsOverlapping}: a
     * locked query only locks the rows it matches, and there may be zero
     * matching rows for a brand-new date range. Safe to run unlocked here
     * because the caller ({@code VehicleHireServiceImpl}) always holds a
     * pessimistic write lock on the parent {@code Vehicle} row first,
     * which serializes every availability check/hire for that vehicle
     * into one at a time.
     */
    @Query("SELECT COUNT(vh) > 0 FROM VehicleHire vh "
            + "WHERE vh.vehicle.id = :vehicleId AND vh.status = :status "
            + "AND vh.startDate <= :endDate AND vh.endDate >= :startDate")
    boolean existsOverlapping(@Param("vehicleId") Long vehicleId,
                              @Param("status") VehicleHireStatus status,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate);

}
