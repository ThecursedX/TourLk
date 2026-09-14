package com.tourlk.repo;

import com.tourlk.entity.Vehicle;
import com.tourlk.enums.VehicleStatus;
import com.tourlk.enums.VehicleType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByStatus(VehicleStatus status);

    List<Vehicle> findByDriverId(Long driverId);

    @Query("SELECT v FROM Vehicle v WHERE v.status = :status "
            + "AND (:vehicleType IS NULL OR v.vehicleType = :vehicleType) "
            + "AND (:minSeatingCapacity IS NULL OR v.seatingCapacity >= :minSeatingCapacity)")
    List<Vehicle> search(@Param("status") VehicleStatus status,
                         @Param("vehicleType") VehicleType vehicleType,
                         @Param("minSeatingCapacity") Integer minSeatingCapacity);

    /**
     * Fetches a vehicle with a pessimistic write lock, held for the rest
     * of the caller's transaction. Same locking pattern as
     * {@code RoomRepository#findByIdForUpdate} in the Accommodation
     * module — see {@code VehicleHireServiceImpl#getLockedVehicle} for
     * why the vehicle row is the thing we lock, not the overlap-check
     * query. Bounded to 5s so a stuck transaction fails fast instead of
     * hanging every other hire attempt for the same vehicle.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT v FROM Vehicle v WHERE v.id = :id")
    Optional<Vehicle> findByIdForUpdate(@Param("id") Long id);

}
