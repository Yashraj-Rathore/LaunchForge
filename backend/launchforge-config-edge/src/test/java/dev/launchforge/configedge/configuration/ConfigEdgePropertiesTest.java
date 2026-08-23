package dev.launchforge.configedge.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.launchforge.contracts.snapshots.SnapshotContract;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ConfigEdgePropertiesTest {
  @Test
  void capsConfiguredSnapshotSizeAtTheVersionOneContractLimit() {
    assertDoesNotThrow(() -> properties(SnapshotContract.MAXIMUM_CANONICAL_SNAPSHOT_BYTES));
    assertThrows(
        IllegalArgumentException.class,
        () -> properties(SnapshotContract.MAXIMUM_CANONICAL_SNAPSHOT_BYTES + 1));
  }

  private static ConfigEdgeProperties properties(int maximumSnapshotBytes) {
    return new ConfigEdgeProperties(
        maximumSnapshotBytes, Duration.ofSeconds(1), Duration.ofSeconds(15), 10, 2);
  }
}
