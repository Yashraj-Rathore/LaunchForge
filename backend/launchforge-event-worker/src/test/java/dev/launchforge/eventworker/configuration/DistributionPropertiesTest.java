package dev.launchforge.eventworker.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.launchforge.contracts.snapshots.SnapshotContract;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DistributionPropertiesTest {
  @Test
  void capsConfiguredSnapshotSizeAtTheVersionOneContractLimit() {
    assertDoesNotThrow(() -> properties(SnapshotContract.MAXIMUM_CANONICAL_SNAPSHOT_BYTES));
    assertThrows(
        IllegalArgumentException.class,
        () -> properties(SnapshotContract.MAXIMUM_CANONICAL_SNAPSHOT_BYTES + 1));
  }

  private static DistributionProperties properties(int maximumSnapshotBytes) {
    return new DistributionProperties(
        "launchforge.config.revision-published.v1",
        "launchforge-config-projector-v1",
        "launchforge:config:revision-hints:v1",
        12,
        1,
        50,
        Duration.ofSeconds(30),
        Duration.ofSeconds(10),
        Duration.ofSeconds(1),
        Duration.ofMinutes(1),
        200,
        maximumSnapshotBytes);
  }
}
