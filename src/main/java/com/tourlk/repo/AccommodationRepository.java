package com.tourlk.repo;

import com.tourlk.entity.Accommodation;
import com.tourlk.enums.AccommodationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AccommodationRepository extends JpaRepository<Accommodation, Long> {

    List<Accommodation> findByStatus(AccommodationStatus status);

    List<Accommodation> findByOwnerId(Long ownerId);

    @Query("SELECT a FROM Accommodation a WHERE a.status = :status "
            + "AND (:locationId IS NULL OR a.location.id = :locationId)")
    List<Accommodation> search(@Param("status") AccommodationStatus status, @Param("locationId") Long locationId);

    /** Used by the Destination module to show "N active hotels here" counts. */
    long countByLocationIdAndStatus(Long locationId, AccommodationStatus status);

}
