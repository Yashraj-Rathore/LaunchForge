package dev.launchforge.contracts.snapshots;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SnapshotContractTest {
  private static final Pattern CONTRACT_MAXIMUM =
      Pattern.compile("\\\"maximumCanonicalUtf8Bytes\\\"\\s*:\\s*(\\d+)");

  @Test
  void acceptsOnlyPositiveLimitsAtOrBelowFiveMebibytes() throws Exception {
    String contract =
        Files.readString(
            repositoryRoot().resolve("contracts/golden-vectors/snapshot-size-v1.json"),
            StandardCharsets.UTF_8);
    var maximum = CONTRACT_MAXIMUM.matcher(contract);

    if (!maximum.find()) {
      throw new AssertionError("Snapshot size contract has no maximumCanonicalUtf8Bytes");
    }
    assertEquals(
        Integer.parseInt(maximum.group(1)), SnapshotContract.MAXIMUM_CANONICAL_SNAPSHOT_BYTES);
    assertEquals(5_242_880, SnapshotContract.MAXIMUM_CANONICAL_SNAPSHOT_BYTES);
    assertDoesNotThrow(() -> SnapshotContract.requireValidMaximumSnapshotBytes(1));
    assertDoesNotThrow(
        () ->
            SnapshotContract.requireValidMaximumSnapshotBytes(
                SnapshotContract.MAXIMUM_CANONICAL_SNAPSHOT_BYTES));
    assertThrows(
        IllegalArgumentException.class, () -> SnapshotContract.requireValidMaximumSnapshotBytes(0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SnapshotContract.requireValidMaximumSnapshotBytes(
                SnapshotContract.MAXIMUM_CANONICAL_SNAPSHOT_BYTES + 1));
  }

  private static Path repositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("contracts/golden-vectors/snapshot-size-v1.json"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("Cannot locate repository root");
  }
}
