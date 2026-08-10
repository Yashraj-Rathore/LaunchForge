package dev.launchforge.application.controlplane;

import dev.launchforge.application.organization.OrganizationAccess;
import dev.launchforge.domain.controlplane.Environment;
import dev.launchforge.domain.controlplane.EnvironmentDraft;
import dev.launchforge.domain.controlplane.FlagDefinition;
import dev.launchforge.domain.controlplane.Project;
import java.time.Instant;
import java.util.List;

public interface SnapshotCodec {
  EncodedSnapshot encode(
      Project project,
      Environment environment,
      List<FlagDefinition> flags,
      List<EnvironmentDraft> drafts,
      long revision,
      Instant publishedAt);

  EncodedSnapshot rebase(String canonicalSnapshot, long revision, Instant publishedAt);

  RevisionDiff diff(String fromCanonicalSnapshot, String toCanonicalSnapshot);

  String publicationEvent(
      OrganizationAccess access,
      Environment environment,
      long revision,
      String checksum,
      Instant occurredAt);

  String canonicalizeJsonValue(String rawJson);

  record EncodedSnapshot(String canonicalJson, String checksum, int utf8Bytes) {}

  record RevisionDiff(
      List<String> addedFlagKeys, List<String> removedFlagKeys, List<String> changedFlagKeys) {
    public RevisionDiff {
      addedFlagKeys = List.copyOf(addedFlagKeys);
      removedFlagKeys = List.copyOf(removedFlagKeys);
      changedFlagKeys = List.copyOf(changedFlagKeys);
    }
  }
}
