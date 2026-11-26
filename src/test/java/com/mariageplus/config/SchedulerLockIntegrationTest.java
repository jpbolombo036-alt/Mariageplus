package com.mariageplus.config;

import com.mariageplus.service.BulkSendWorker;
import com.mariageplus.service.EventAutoLifecycleService;
import com.mariageplus.service.EventReminderService;
import com.mariageplus.service.InvitationReminderService;
import com.mariageplus.service.VenueBookingService;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.annotation.DirtiesContext;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verrou partagé des tâches planifiées : avec plusieurs instances du backend,
 * une seule exécute chaque tâche. La base (table shedlock, V56) fait foi.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SchedulerLockIntegrationTest {

    @Autowired private LockProvider lockProvider;
    @Autowired private CountingTask countingTask;

    @Test
    void onlyOneHolderAtATime() {
        Optional<SimpleLock> first = lockProvider.lock(config("test-exclusive"));
        assertThat(first).isPresent();
        // Une « autre instance » ne l'obtient pas tant qu'il est tenu.
        assertThat(lockProvider.lock(config("test-exclusive"))).isEmpty();
        first.get().unlock();
        Optional<SimpleLock> again = lockProvider.lock(config("test-exclusive"));
        assertThat(again).isPresent();
        again.get().unlock();
    }

    @Test
    void lockedTaskIsSkippedWhileAnotherInstanceHoldsTheLock() {
        Optional<SimpleLock> otherInstance = lockProvider.lock(config(CountingTask.LOCK));
        assertThat(otherInstance).isPresent();
        countingTask.run();
        assertThat(countingTask.count()).as("tâche sautée : une autre instance la détient").isZero();

        otherInstance.get().unlock();
        countingTask.run();
        assertThat(countingTask.count()).isEqualTo(1);
    }

    @Test
    void everySharedScheduledTaskIsLockedUnderItsOwnName() {
        List<String> names = new ArrayList<>();
        for (Class<?> type : List.of(BulkSendWorker.class, EventAutoLifecycleService.class, EventReminderService.class,
                InvitationReminderService.class, VenueBookingService.class)) {
            for (Method m : type.getDeclaredMethods()) {
                if (m.isAnnotationPresent(Scheduled.class)) {
                    SchedulerLock lock = m.getAnnotation(SchedulerLock.class);
                    assertThat(lock).as(type.getSimpleName() + "." + m.getName() + " doit être verrouillée").isNotNull();
                    names.add(lock.name());
                }
            }
        }
        assertThat(names).hasSize(5).doesNotHaveDuplicates();
    }

    private static LockConfiguration config(String name) {
        return new LockConfiguration(Instant.now(), name, Duration.ofMinutes(1), Duration.ZERO);
    }

    @TestConfiguration
    static class Tasks {
        @Bean
        CountingTask countingTask() {
            return new CountingTask();
        }
    }

    static class CountingTask {
        static final String LOCK = "test-counting-task";
        final AtomicInteger runs = new AtomicInteger();

        @SchedulerLock(name = LOCK, lockAtMostFor = "PT1M")
        public void run() {
            runs.incrementAndGet();
        }

        /** Lu par méthode : la tâche est un proxy (verrou), ses champs ne sont pas exposés. */
        public int count() {
            return runs.get();
        }
    }
}
