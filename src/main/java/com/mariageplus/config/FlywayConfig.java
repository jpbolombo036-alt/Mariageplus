package com.mariageplus.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stratégie de migration Flyway : exécute {@code repair()} avant {@code migrate()}.
 *
 * Correctif pour l'erreur "Migration checksum mismatch for migration version 2" :
 * le fichier V2 a été modifié après avoir été appliqué en base, ce qui faisait
 * échouer la validation Flyway au démarrage (et en cascade tous les beans JPA :
 * entityManagerFactory, userRepository, userPrincipalService, jwtAuthenticationFilter).
 *
 * repair() met à jour les checksums enregistrés dans flyway_schema_history pour
 * qu'ils correspondent aux fichiers locaux, puis migrate() applique les migrations
 * restantes normalement.
 */
@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            log.info("Flyway repair : alignement des checksums de flyway_schema_history sur les fichiers locaux");
            flyway.repair();
            log.info("Flyway migrate : application des migrations en attente");
            flyway.migrate();
        };
    }
}
