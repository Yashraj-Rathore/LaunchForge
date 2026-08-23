package dev.launchforge.contracts.snapshots;

/** Version-1 limits shared by backend snapshot producers and consumers. */
public final class SnapshotContract {
  public static final int MAXIMUM_CANONICAL_SNAPSHOT_BYTES = 5 * 1024 * 1024;

  private SnapshotContract() {}

  public static void requireValidMaximumSnapshotBytes(int configuredMaximum) {
    if (configuredMaximum < 1 || configuredMaximum > MAXIMUM_CANONICAL_SNAPSHOT_BYTES) {
      throw new IllegalArgumentException(
          "maximumSnapshotBytes must be between 1 and " + MAXIMUM_CANONICAL_SNAPSHOT_BYTES);
    }
  }
}
