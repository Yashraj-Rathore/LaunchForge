package dev.launchforge.eventworker.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class WorkerSchedulingConfigurationTest {
  @Test
  void usesFiniteIndependentPoolsForConfigurationAndAnalytics() {
    WorkerSchedulingConfiguration configuration = new WorkerSchedulingConfiguration();

    ThreadPoolTaskScheduler configurationScheduler = configuration.configurationTaskScheduler();
    ThreadPoolTaskScheduler analyticsScheduler = configuration.analyticsTaskScheduler();

    assertEquals(2, configurationScheduler.getPoolSize());
    assertEquals(1, analyticsScheduler.getPoolSize());
  }

  @Test
  void routesScheduledJobsToTheirDedicatedPool() throws Exception {
    assertScheduler(
        "dev.launchforge.eventworker.analytics.AnalyticsEventBuffer",
        "flush",
        WorkerSchedulingConfiguration.ANALYTICS_SCHEDULER);
    assertScheduler(
        "dev.launchforge.eventworker.outbox.OutboxPublisher",
        "publishAvailable",
        WorkerSchedulingConfiguration.CONFIGURATION_SCHEDULER);
    assertScheduler(
        "dev.launchforge.eventworker.projection.ProjectionReconciler",
        "reconcile",
        WorkerSchedulingConfiguration.CONFIGURATION_SCHEDULER);
  }

  private static void assertScheduler(String className, String methodName, String expected)
      throws Exception {
    Method method = Class.forName(className).getDeclaredMethod(methodName);
    assertEquals(expected, method.getAnnotation(Scheduled.class).scheduler());
  }
}
