package com.tourlk.repo;

import com.tourlk.entity.Accommodation;
import com.tourlk.enums.AccommodationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AccommodationRepository extends JpaRepository<Accommodation, Long> {

    List<Accommodation> findByStatus(AccommodationStatus status);

    List<Accommodation> findByOwnerId(Long ownerId);

    long countByOwnerId(Long ownerId);

    @Query("SELECT a FROM Accommodation a WHERE a.status IN :statuses "
            + "AND (:locationId IS NULL OR a.location.id = :locationId)")
    List<Accommodation> search(@Param("statuses") Collection<AccommodationStatus> statuses,
                               @Param("locationId") Long locationId);

    /** Used by the Destination module to show "N active hotels here" counts. */
    long countByLocationIdAndStatus(Long locationId, AccommodationStatus status);

}
