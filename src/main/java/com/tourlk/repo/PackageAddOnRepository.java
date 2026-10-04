package com.tourlk.repo;

import com.tourlk.entity.PackageAddOn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PackageAddOnRepository extends JpaRepository<PackageAddOn, Long> {

    @Query("SELECT a FROM PackageAddOn a LEFT JOIN FETCH a.room r LEFT JOIN FETCH r.accommodation "
            + "LEFT JOIN FETCH a.vehicle WHERE a.tourPackage.id = :packageId ORDER BY a.id")
    List<PackageAddOn> findByTourPackageId(@Param("packageId") Long tourPackageId);

    @Query("SELECT a FROM PackageAddOn a LEFT JOIN FETCH a.room r LEFT JOIN FETCH r.accommodation "
            + "LEFT JOIN FETCH a.vehicle WHERE a.tourPackage.id IN :packageIds ORDER BY a.id")
    List<PackageAddOn> findByTourPackageIdIn(@Param("packageIds") Collection<Long> tourPackageIds);

}
