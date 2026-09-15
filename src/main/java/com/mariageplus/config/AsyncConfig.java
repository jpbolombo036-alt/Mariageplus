package com.mariageplus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Executeur dedie aux envois en masse : N workers (configurable via
 * app.whatsapp.bulk.workers, defaut 2) afin qu'un gros batch d'une organisation
 * ne bloque pas les envois des autres - l'ordre au sein d'un batch reste
 * garanti (boucle sequentielle) et chaque message reste cadence
 * (app.whatsapp.bulk.delay-ms). File bornee pour eviter l'accumulation
 * infinie de batches.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    private final int workers;

    public AsyncConfig(@Value("${app.whatsapp.bulk.workers:2}") int workers) {
        this.workers = Math.max(1, workers);
    }

    @Bean(name = "bulkSendExecutor")
    public ThreadPoolTaskExecutor bulkSendExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("bulk-send-");
        executor.initialize();
        return executor;
    }
}
