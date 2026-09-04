package com.mariageplus.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Exécuteur dédié aux envois en masse : un seul worker (files traitées dans
 * l'ordre, cadencement simple des appels API), file bornée pour éviter
 * l'accumulation infinie de batches.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "bulkSendExecutor")
    public ThreadPoolTaskExecutor bulkSendExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("bulk-send-");
        executor.initialize();
        return executor;
    }
}
