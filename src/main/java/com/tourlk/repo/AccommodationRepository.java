package com.tourlk.repo;

import com.tourlk.entity.Accommodation;
import com.tourlk.enums.AccommodationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface AccommodationRepository extends JpaRepository<Accommodation, Long> {

    List<Accommodation> findByStatus(AccommodationStatus status);

    List<Accommodation> findByOwnerId(Long ownerId);

    long countByOwnerId(Long ownerId);

    /**
     * Public browse. Every filter is optional (null = ignored). {@code namePattern} is a ready-made
     * lower-case LIKE pattern whose wildcards were escaped with {@code !} (see
     * {@code AccommodationServiceImpl#likePattern}). The price range matches a listing when at least
     * one of its room types is priced inside it (inclusive; either bound may be null).
     */
    @Query("SELECT a FROM Accommodation a WHERE a.status IN :statuses "
            + "AND (:locationId IS NULL OR a.location.id = :locationId) "
            + "AND (:namePattern IS NULL OR LOWER(a.name) LIKE :namePattern ESCAPE '!') "
            + "AND (:minStars IS NULL OR a.starRating >= :minStars) "
            + "AND ((:minPrice IS NULL AND :maxPrice IS NULL) OR EXISTS ("
            + "SELECT r.id FROM Room r WHERE r.accommodation = a "
            + "AND (:minPrice IS NULL OR r.pricePerNight >= :minPrice) "
            + "AND (:maxPrice IS NULL OR r.pricePerNight <= :maxPrice)))")
    List<Accommodation> search(@Param("statuses") Collection<AccommodationStatus> statuses,
                               @Param("locationId") Long locationId,
                               @Param("namePattern") String namePattern,
                               @Param("minStars") Integer minStars,
                               @Param("minPrice") BigDecimal minPrice,
                               @Param("maxPrice") BigDecimal maxPrice);

    /** Used by the Destination module to show "N active hotels here" counts. */
    long countByLocationIdAndStatus(Long locationId, AccommodationStatus status);

}
