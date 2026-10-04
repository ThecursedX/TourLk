package com.tourlk.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * One-off upgrade of the enum-backed status columns whose enums grew
 * (this project has no migration framework; Hibernate {@code ddl-auto:
 * update} never alters an existing column or CHECK constraint):
 * <ul>
 *   <li>{@code vehicles.status}: ACTIVE -&gt; AVAILABLE, PENDING_APPROVAL
 *       -&gt; PENDING_VERIFICATION, INACTIVE -&gt; OUT_OF_SERVICE</li>
 *   <li>{@code accommodations.status}: widened for TEMPORARILY_UNAVAILABLE (23 chars)</li>
 *   <li>{@code destinations.status}: ACTIVE -&gt; PUBLISHED, widened for the new statuses</li>
 *   <li>{@code support_tickets.status}: CHECK constraint dropped for WAITING_FOR_USER / WITHDRAWN</li>
 *   <li>{@code notifications.type}: new VEHICLE_* types</li>
 * </ul>
 * SQL Server only (skipped on H2 test databases) and idempotent: it drops
 * the stale CHECK constraints Hibernate generated for these columns (so the
 * new values can be stored), widens the varchar columns, then rewrites the
 * legacy vehicle values. A fresh database has nothing to change.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatusMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!isSqlServer()) {
            return;
        }

        try {
            migrateColumn("vehicles", "status", 30);
            migrateColumn("accommodations", "status", 30);
            migrateColumn("destinations", "status", 30);
            migrateColumn("support_tickets", "status", 20);
            migrateColumn("notifications", "type", 40);

            migrateVehicleStatus("ACTIVE", "AVAILABLE");
            migrateVehicleStatus("PENDING_APPROVAL", "PENDING_VERIFICATION");
            migrateVehicleStatus("INACTIVE", "OUT_OF_SERVICE");

            int published = jdbcTemplate.update("UPDATE destinations SET status = 'PUBLISHED' WHERE status = 'ACTIVE'");
            if (published > 0) {
                log.info("Migrated {} destination(s) from ACTIVE to PUBLISHED", published);
            }
        } catch (RuntimeException e) {
            // Never block startup; on a fresh DB the tables may not exist yet to migrate.
            log.warn("Status column migration skipped/failed: {}", e.getMessage());
        }
    }

    private boolean isSqlServer() {
        try {
            String product = jdbcTemplate.execute(
                    (java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
            return product != null && product.toLowerCase().contains("sql server");
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void migrateColumn(String table, String column, int length) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
        if (exists == null || exists == 0) {
            return;
        }

        List<Map<String, Object>> checks = jdbcTemplate.queryForList(
                "SELECT cc.name AS name FROM sys.check_constraints cc "
                        + "JOIN sys.columns col ON col.object_id = cc.parent_object_id "
                        + "AND col.column_id = cc.parent_column_id "
                        + "WHERE cc.parent_object_id = OBJECT_ID(?) AND col.name = ?",
                "dbo." + table, column);
        for (Map<String, Object> check : checks) {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT [" + check.get("name") + "]");
            log.info("Dropped stale check constraint {} on {}.{}", check.get("name"), table, column);
        }

        Integer currentLength = jdbcTemplate.queryForObject(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
        if (currentLength != null && currentLength > 0 && currentLength < length) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ALTER COLUMN " + column
                    + " VARCHAR(" + length + ") NOT NULL");
            log.info("Widened {}.{} to varchar({})", table, column, length);
        }
    }

    private void migrateVehicleStatus(String from, String to) {
        int updated = jdbcTemplate.update("UPDATE vehicles SET status = ? WHERE status = ?", to, from);
        if (updated > 0) {
            log.info("Migrated {} vehicle(s) from {} to {}", updated, from, to);
        }
    }

}
