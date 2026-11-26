package com.mariageplus.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Verrou partagé des tâches planifiées (ShedLock, table {@code shedlock}, V56).
 * Si plusieurs instances du backend tournent (mise à l'échelle, chevauchement
 * pendant un redéploiement), une seule exécute chaque tâche @SchedulerLock :
 * pas de rappel, de relance ou d'expiration en double. L'heure de la base sert
 * de référence pour ne pas dépendre de l'horloge de chaque instance.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
