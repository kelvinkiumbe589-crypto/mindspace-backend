package com.mindspace.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Small idempotent schema tweaks that Hibernate's ddl-auto=update can't perform.
 * `update` adds new columns but never relaxes existing constraints, so schema
 * changes like making a previously-required column nullable are applied here.
 */
@Component
public class SchemaMigrations implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrations.class);

    private final JdbcTemplate jdbc;

    public SchemaMigrations(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Guest (not-logged-in) support conversations store a null user_id, but the
        // column was originally NOT NULL. Dropping it is a no-op once already applied.
        run("ALTER TABLE support_messages ALTER COLUMN user_id DROP NOT NULL");

        // Google Maps links overflow the original 255-char columns — widen them.
        run("ALTER TABLE therapist_profiles ALTER COLUMN practice_map_url TYPE text");
        run("ALTER TABLE therapist_profiles ALTER COLUMN practice_address TYPE varchar(500)");
        run("ALTER TABLE therapist_profiles ALTER COLUMN practice_notes TYPE varchar(500)");

        // Don't nudge users about admin replies that already existed before this
        // feature shipped — treat older replies as already seen.
        run("UPDATE support_messages SET seen_by_user = true WHERE from_admin = true AND created_at < now() - interval '12 hours'");
    }

    private void run(String sql) {
        try {
            jdbc.execute(sql);
            log.info("Schema migration applied: {}", sql);
        } catch (Exception e) {
            log.warn("Schema migration skipped ({}): {}", sql, e.getMessage());
        }
    }
}
