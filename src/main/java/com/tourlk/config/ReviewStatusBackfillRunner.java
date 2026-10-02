package com.tourlk.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-off data backfill for the {@code reviews.status} column that
 * replaced the old {@code flagged} boolean. This project has no
 * migration framework (Hibernate {@code ddl-auto: update} just adds the
 * new nullable column on its own) — this fills it in for rows that
 * predate the column, and is a no-op on every later startup once every
 * row has a status.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewStatusBackfillRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        // A fresh database never had the old `flagged` column, so this must
        // check for it rather than assuming it's there — only a DB that's
        // being upgraded from before this change has anything to backfill.
        Integer flaggedColumnExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_NAME = 'reviews' AND COLUMN_NAME = 'flagged'",
                Integer.class);
        if (flaggedColumnExists == null || flaggedColumnExists == 0) {
            return;
        }

        int updated = jdbcTemplate.update(
                "UPDATE reviews SET status = CASE WHEN flagged = 1 THEN 'REPORTED' ELSE 'PUBLISHED' END "
                        + "WHERE status IS NULL");
        if (updated > 0) {
            log.info("Backfilled status for {} legacy review(s)", updated);
        }
    }

}
