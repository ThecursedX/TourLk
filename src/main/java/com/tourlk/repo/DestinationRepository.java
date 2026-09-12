package com.tourlk.repo;

import com.tourlk.entity.Destination;
import com.tourlk.enums.DestinationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DestinationRepository extends JpaRepository<Destination, Long> {

    List<Destination> findByStatus(DestinationStatus status);

    List<Destination> findByNameContainingIgnoreCase(String name);

    List<Destination> findByRegion(String region);

    /** Case-insensitive exact match — used by the service to enforce name uniqueness. */
    Optional<Destination> findByNameIgnoreCase(String name);

}
