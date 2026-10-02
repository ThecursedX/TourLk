package com.tourlk.repo;

import com.tourlk.entity.TourPackage;
import com.tourlk.enums.PackageStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Browsing/filtering goes through {@link JpaSpecificationExecutor} with
 * {@link TourPackageSpecifications}.
 */
public interface TourPackageRepository extends JpaRepository<TourPackage, Long>,
        JpaSpecificationExecutor<TourPackage> {

    List<TourPackage> findByStatus(PackageStatus status);

    List<TourPackage> findByStatus(PackageStatus status, Sort sort);

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

    /** Used by the Destination module to show "N active packages here" counts. */
    long countByDestinationIdAndStatus(Long destinationId, PackageStatus status);

}
