package com.tourlk.repo;

import com.tourlk.entity.Destination;
import com.tourlk.enums.DestinationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DestinationRepository extends JpaRepository<Destination, Long> {

    List<Destination> findByStatus(DestinationStatus status);

    List<Destination> findByNameContainingIgnoreCase(String name);

    List<Destination> findByRegion(String region);

    /** Every category currently in use, for the admin form's suggestions. */
    @Query("SELECT DISTINCT d.category FROM Destination d WHERE d.category IS NOT NULL")
    List<String> findDistinctCategories();

    /** Case-insensitive exact match — used by the service to enforce name uniqueness. */
    Optional<Destination> findByNameIgnoreCase(String name);

}
