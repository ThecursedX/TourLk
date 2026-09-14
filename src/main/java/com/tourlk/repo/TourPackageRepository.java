package com.tourlk.repo;

import com.tourlk.entity.TourPackage;
import com.tourlk.enums.PackageStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TourPackageRepository extends JpaRepository<TourPackage, Long> {

    List<TourPackage> findByStatus(PackageStatus status);

    List<TourPackage> findByCreatedById(Long userId);

    /**
     * Fetches a package with a pessimistic write lock (row-level "FOR
     * UPDATE"), held for the rest of the caller's transaction. Used by
     * {@code BookingServiceImpl} to serialize capacity checks — see the
     * comment on {@code BookingServiceImpl#getLockedPackage} for why the
     * package row is the thing we lock, not the booking count query.
     * Bounded to 5s so a stuck transaction fails fast instead of hanging
     * every other booking attempt for the same package.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT p FROM TourPackage p WHERE p.id = :id")
    Optional<TourPackage> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT p FROM TourPackage p WHERE p.status = :status "
            + "AND (:destinationId IS NULL OR p.destination.id = :destinationId) "
            + "AND (:minPrice IS NULL OR p.price >= :minPrice) "
            + "AND (:maxPrice IS NULL OR p.price <= :maxPrice)")
    List<TourPackage> search(@Param("status") PackageStatus status,
                              @Param("destinationId") Long destinationId,
                              @Param("minPrice") BigDecimal minPrice,
                              @Param("maxPrice") BigDecimal maxPrice);

    /** Used by the Destination module to show "N active packages here" counts. */
    long countByDestinationIdAndStatus(Long destinationId, PackageStatus status);

}
