package dev.launchforge.eventworker.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Isolates lower-priority analytics I/O from configuration distribution work. */
@Configuration(proxyBeanMethods = false)
public class WorkerSchedulingConfiguration {
  public static final String CONFIGURATION_SCHEDULER = "configurationTaskScheduler";
  public static final String ANALYTICS_SCHEDULER = "analyticsTaskScheduler";

  private static final int CONFIGURATION_THREADS = 2;
  private static final int ANALYTICS_THREADS = 1;

  @Bean(name = CONFIGURATION_SCHEDULER)
  ThreadPoolTaskScheduler configurationTaskScheduler() {
    return scheduler(CONFIGURATION_THREADS, "launchforge-configuration-");
  }

  @Bean(name = ANALYTICS_SCHEDULER)
  @ConditionalOnProperty(name = "launchforge.analytics.worker.enabled", havingValue = "true")
  ThreadPoolTaskScheduler analyticsTaskScheduler() {
    return scheduler(ANALYTICS_THREADS, "launchforge-analytics-");
  }

  private static ThreadPoolTaskScheduler scheduler(int poolSize, String threadNamePrefix) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(poolSize);
    scheduler.setThreadNamePrefix(threadNamePrefix);
    scheduler.setRemoveOnCancelPolicy(true);
    scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    scheduler.setWaitForTasksToCompleteOnShutdown(false);
    return scheduler;
  }
}
