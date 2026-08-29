package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * V18 : repositionne les séquences après l'insertion explicite des IDs
 * (V17 a copié weddings → events en conservant les IDs).
 *
 * La syntaxe de reposition dépend du moteur :
 * - PostgreSQL (production) : SELECT setval(pg_get_serial_sequence(...), ...)
 * - H2 (tests) : ALTER TABLE ... ALTER COLUMN id RESTART WITH ...
 *
 * Offset de 1 000 000 pour garantir l'absence de collision avec les IDs copiés.
 */
public class V18__fix_event_sequences extends BaseJavaMigration {

    private static final long OFFSET = 1_000_000L;

    @Override
    public void migrate(Context context) throws Exception {
        String product = context.getConnection().getMetaData().getDatabaseProductName();
        boolean isPostgres = product != null && product.toLowerCase().contains("postgresql");

        try (Statement statement = context.getConnection().createStatement()) {
            if (isPostgres) {
                for (String table : new String[]{"events", "wedding_details", "event_sessions"}) {
                    statement.execute("SELECT setval(pg_get_serial_sequence('" + table + "', 'id'), "
                            + OFFSET + ", false)");
                }
            } else {
                // H2 (MODE=PostgreSQL) : syntaxe ALTER TABLE ... RESTART
                for (String table : new String[]{"events", "wedding_details", "event_sessions"}) {
                    statement.execute("ALTER TABLE " + table + " ALTER COLUMN id RESTART WITH " + OFFSET);
                }
            }
        }
    }
}
