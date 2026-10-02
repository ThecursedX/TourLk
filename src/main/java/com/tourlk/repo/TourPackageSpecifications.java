package com.tourlk.repo;

import com.tourlk.entity.PackageDeparture;
import com.tourlk.entity.TourPackage;
import com.tourlk.enums.BudgetTier;
import com.tourlk.enums.PackageStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaExpression;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Composable filters for browsing tour packages. Each factory returns
 * null for a null argument, which {@link Specification#where}/{@code and}
 * treat as "no restriction", so callers can chain every optional filter
 * unconditionally.
 */
public final class TourPackageSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private TourPackageSpecifications() {
    }

    public static Specification<TourPackage> hasStatus(PackageStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<TourPackage> inDestination(Long destinationId) {
        if (destinationId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("destination").get("id"), destinationId);
    }

    public static Specification<TourPackage> priceBetween(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return null;
        }
        return (root, query, cb) -> {
            Expression<BigDecimal> price = root.get("price");
            if (min == null) {
                return cb.lessThanOrEqualTo(price, max);
            }
            if (max == null) {
                return cb.greaterThanOrEqualTo(price, min);
            }
            return cb.between(price, min, max);
        };
    }

    public static Specification<TourPackage> durationBetween(Integer minDays, Integer maxDays) {
        if (minDays == null && maxDays == null) {
            return null;
        }
        return (root, query, cb) -> {
            Expression<Integer> days = root.get("durationDays");
            if (minDays == null) {
                return cb.lessThanOrEqualTo(days, maxDays);
            }
            if (maxDays == null) {
                return cb.greaterThanOrEqualTo(days, minDays);
            }
            return cb.between(days, minDays, maxDays);
        };
    }

    /**
     * Case-insensitive "contains" on title or description; LIKE wildcards
     * in the text are matched literally. {@code description} is a
     * {@code @Lob}, and Hibernate 6 refuses {@code lower()} on a LOB-typed
     * expression (on every dialect), so it's cast to a plain string first.
     */
    public static Specification<TourPackage> textContains(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> {
            Expression<String> description = ((HibernateCriteriaBuilder) cb)
                    .cast((JpaExpression<String>) root.<String>get("description"), String.class);
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(description), pattern, LIKE_ESCAPE));
        };
    }

    /**
     * Price-per-day band, expressed as {@code price < threshold * durationDays}
     * rather than a division so it matches {@code BudgetTierPolicy#classify}
     * exactly and avoids integer/decimal division differences across databases.
     */
    public static Specification<TourPackage> inBudgetTier(BudgetTier tier, BigDecimal standardMinPerDay,
                                                          BigDecimal luxuryMinPerDay) {
        if (tier == null) {
            return null;
        }
        return (root, query, cb) -> {
            Expression<BigDecimal> price = root.get("price");
            Expression<Integer> days = root.get("durationDays");
            Expression<Number> standardFloor = cb.prod(days, standardMinPerDay);
            Expression<Number> luxuryFloor = cb.prod(days, luxuryMinPerDay);
            return switch (tier) {
                case BUDGET -> cb.lt(price, standardFloor);
                case STANDARD -> cb.and(cb.ge(price, standardFloor), cb.lt(price, luxuryFloor));
                case LUXURY -> cb.ge(price, luxuryFloor);
            };
        };
    }

    /**
     * Keeps packages that have a departure on or after {@code date}, and
     * packages with no departures at all (they accept any date, so the
     * travel-date filter doesn't apply to them).
     */
    public static Specification<TourPackage> departsOnOrAfter(LocalDate date) {
        if (date == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> anyDeparture = departureSubquery(root, query.subquery(Long.class), cb, null);
            Subquery<Long> matchingDeparture = departureSubquery(root, query.subquery(Long.class), cb, date);
            return cb.or(cb.not(cb.exists(anyDeparture)), cb.exists(matchingDeparture));
        };
    }

    private static Subquery<Long> departureSubquery(Root<TourPackage> pkg, Subquery<Long> subquery,
                                                    CriteriaBuilder cb, LocalDate onOrAfter) {
        Root<PackageDeparture> departure = subquery.from(PackageDeparture.class);
        Predicate samePackage = cb.equal(departure.get("tourPackage"), pkg);
        Predicate where = onOrAfter == null
                ? samePackage
                : cb.and(samePackage, cb.greaterThanOrEqualTo(departure.get("departureDate"), onOrAfter));
        return subquery.select(departure.get("id")).where(where);
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
