package com.mariageplus.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stratégie de migration Flyway.
 *
 * Par défaut : {@code migrate()} seul. La validation des checksums reste active,
 * de sorte qu'une migration déjà appliquée puis modifiée bloque le démarrage
 * (comportement voulu : on ne réécrit pas l'historique du schéma en silence).
 *
 * Dépannage ponctuel : avec {@code FLYWAY_REPAIR_ON_START=true}, un
 * {@code repair()} est exécuté avant {@code migrate()} pour réaligner les
 * checksums de flyway_schema_history sur les fichiers locaux (cas historique :
 * "Migration checksum mismatch for migration version 2"). À désactiver dès que
 * le déploiement est passé.
 */
@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Value("${app.flyway.repair-on-start:false}")
    private boolean repairOnStart;

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            if (repairOnStart) {
                log.warn("Flyway repair activé (app.flyway.repair-on-start=true) : "
                        + "réalignement des checksums de flyway_schema_history. A désactiver après usage.");
                flyway.repair();
            }
            flyway.migrate();
        };
    }
}
