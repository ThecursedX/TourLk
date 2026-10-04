package com.tourlk.repo;

import com.tourlk.entity.PackageDeparture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PackageDepartureRepository extends JpaRepository<PackageDeparture, Long> {

    List<PackageDeparture> findByTourPackageIdAndDepartureDateAfterOrderByDepartureDateAsc(Long tourPackageId,
                                                                                           LocalDate after);

    Optional<PackageDeparture> findByTourPackageIdAndDepartureDate(Long tourPackageId, LocalDate departureDate);

    Optional<PackageDeparture> findByIdAndTourPackageId(Long id, Long tourPackageId);

    boolean existsByTourPackageId(Long tourPackageId);

    /** Which of the given packages have at least one departure — one query for a whole listing. */
    @Query("SELECT DISTINCT d.tourPackage.id FROM PackageDeparture d WHERE d.tourPackage.id IN :packageIds")
    List<Long> findPackageIdsWithDepartures(@Param("packageIds") Collection<Long> packageIds);

    boolean existsByTourPackageIdAndDepartureDate(Long tourPackageId, LocalDate departureDate);

}
