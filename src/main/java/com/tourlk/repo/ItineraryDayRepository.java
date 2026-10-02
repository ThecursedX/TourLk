package com.tourlk.repo;

import com.tourlk.entity.ItineraryDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ItineraryDayRepository extends JpaRepository<ItineraryDay, Long> {

    /** Batch load for mapping a list of packages without one query per package. */
    List<ItineraryDay> findByTourPackageIdInOrderByDayNumberAsc(Collection<Long> tourPackageIds);

    void deleteByTourPackageId(Long tourPackageId);

}
