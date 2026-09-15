package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.sql.SQLException;
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
 *
 * Défensif : si une table n'existe pas encore (état inattendu de l'historique
 * Flyway, ex. reprise après échec), on la saute au lieu de faire échouer tout
 * le démarrage — le repositionnement n'a de sens que sur une table existante.
 */
public class V18__fix_event_sequences extends BaseJavaMigration {

    private static final long OFFSET = 1_000_000L;

    @Override
    public void migrate(Context context) throws Exception {
        String product = context.getConnection().getMetaData().getDatabaseProductName();
        boolean isPostgres = product != null && product.toLowerCase().contains("postgresql");

        try (Statement statement = context.getConnection().createStatement()) {
            for (String table : new String[]{"events", "wedding_details", "event_sessions"}) {
                if (!tableExists(context, table)) {
                    continue;
                }
                if (isPostgres) {
                    statement.execute("SELECT setval(pg_get_serial_sequence('" + table + "', 'id'), "
                            + OFFSET + ", false)");
                } else {
                    // H2 (MODE=PostgreSQL) : syntaxe ALTER TABLE ... RESTART
                    statement.execute("ALTER TABLE " + table + " ALTER COLUMN id RESTART WITH " + OFFSET);
                }
            }
        }
    }

    /** true si la table existe (recherche insensible à la casse via les métadonnées JDBC). */
    private boolean tableExists(Context context, String table) throws SQLException {
        try (ResultSet rs = context.getConnection().getMetaData().getTables(
                null, null, table, new String[]{"TABLE"})) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = context.getConnection().getMetaData().getTables(
                null, null, table.toUpperCase(), new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}

