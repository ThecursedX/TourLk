package com.tourlk.repo;

import com.tourlk.entity.Payment;
import com.tourlk.entity.User;
import com.tourlk.enums.PayableType;
import com.tourlk.enums.PaymentStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Optional filters for the admin payments table. Each factory returns null
 * for a missing argument, which {@link Specification#where}/{@code and}
 * treat as "no restriction".
 */
public final class PaymentSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private PaymentSpecifications() {
    }

    public static Specification<Payment> hasStatus(PaymentStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Payment> hasPayableType(PayableType payableType) {
        return payableType == null ? null : (root, query, cb) -> cb.equal(root.get("payableType"), payableType);
    }

    /** Payer name/email contains the term; a purely numeric term also matches the payable id or payment id. */
    public static Specification<Payment> matchesSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = search.trim();
        return (root, query, cb) -> {
            Join<Payment, User> payer = root.join("payer");
            String pattern = "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%";
            Predicate match = cb.or(
                    cb.like(cb.lower(payer.get("name")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(payer.get("email")), pattern, LIKE_ESCAPE));
            if (term.matches("\\d{1,18}")) {
                long number = Long.parseLong(term);
                match = cb.or(match, cb.equal(root.get("payableId"), number), cb.equal(root.get("id"), number));
            }
            return match;
        };
    }

    /** Inclusive of both days; either bound may be null. */
    public static Specification<Payment> createdBetween(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return null;
        }
        return (root, query, cb) -> {
            Predicate result = cb.conjunction();
            if (from != null) {
                result = cb.and(result, cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            }
            if (to != null) {
                result = cb.and(result, cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            }
            return result;
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
