package dev.launchforge.infrastructure.controlplane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.launchforge.application.controlplane.SnapshotCodec.EncodedSnapshot;
import dev.launchforge.domain.controlplane.ControlPlaneRuleViolationException;
import dev.launchforge.domain.controlplane.Environment;
import dev.launchforge.domain.controlplane.EnvironmentDraft;
import dev.launchforge.domain.controlplane.EnvironmentId;
import dev.launchforge.domain.controlplane.FlagDefinition;
import dev.launchforge.domain.controlplane.FlagDefinition.FlagType;
import dev.launchforge.domain.controlplane.FlagDefinition.FlagValue;
import dev.launchforge.domain.controlplane.FlagDefinition.Variation;
import dev.launchforge.domain.controlplane.FlagId;
import dev.launchforge.domain.controlplane.Project;
import dev.launchforge.domain.controlplane.ProjectId;
import dev.launchforge.domain.controlplane.ResourceKey;
import dev.launchforge.domain.controlplane.Targeting.Allocation;
import dev.launchforge.domain.controlplane.Targeting.AttributeType;
import dev.launchforge.domain.controlplane.Targeting.Condition;
import dev.launchforge.domain.controlplane.Targeting.Operator;
import dev.launchforge.domain.controlplane.Targeting.PercentageRollout;
import dev.launchforge.domain.controlplane.Targeting.Rule;
import dev.launchforge.domain.organization.OrganizationId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.erdtman.jcs.JsonCanonicalizer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class JacksonSnapshotCodecTest {
  private static final Instant NOW = Instant.parse("2026-08-10T17:00:00Z");
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JacksonSnapshotCodec codec = new JacksonSnapshotCodec(objectMapper);

  @Test
  void emitsNormativeRuntimeShapeAndChecksumOverProjectionWithoutChecksum() throws Exception {
    Fixture fixture = fixture();

    EncodedSnapshot encoded =
        codec.encode(
            fixture.project(),
            fixture.environment(),
            List.of(fixture.flag()),
            List.of(fixture.draft()),
            42,
            NOW);

    JsonNode root = objectMapper.readTree(encoded.canonicalJson());
    assertEquals(1, root.path("schemaVersion").intValue());
    assertEquals(1, root.path("algorithmVersion").intValue());
    assertEquals("northstar.storefront", root.path("projectKey").stringValue());
    assertEquals("production", root.path("environmentKey").stringValue());
    assertEquals(42L, root.path("revision").longValue());
    assertNull(root.get("environment"));
    JsonNode flag = root.path("flags").path("new-checkout");
    assertEquals("boolean", flag.path("type").stringValue());
    assertTrue(flag.path("clientVisible").booleanValue());
    assertNull(flag.get("key"));
    assertNull(flag.get("id"));
    assertEquals("off", flag.path("offVariation").stringValue());
    assertEquals("on", flag.path("defaultVariation").stringValue());
    assertEquals("off", flag.path("variations").get(0).path("id").stringValue());
    assertEquals("userId", flag.path("rollout").path("attribute").stringValue());
    assertEquals("on", flag.path("rollout").path("weights").get(0).path("variation").stringValue());
    assertFalse(encoded.canonicalJson().contains("rolloutSalt"));

    ObjectNode withoutChecksum = (ObjectNode) root.deepCopy();
    withoutChecksum.remove("checksum");
    String canonicalWithoutChecksum =
        new JsonCanonicalizer(objectMapper.writeValueAsString(withoutChecksum)).getEncodedString();
    String expectedChecksum =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(canonicalWithoutChecksum.getBytes(StandardCharsets.UTF_8)));
    assertEquals(expectedChecksum, encoded.checksum());
    assertEquals(expectedChecksum, root.path("checksum").stringValue());
  }

  @Test
  void omitsAbsentRolloutAndRebasesWithoutChangingFlagContent() throws Exception {
    Fixture fixture = fixture();
    EnvironmentDraft noRollout =
        new EnvironmentDraft(
            fixture.draft().flagId(),
            fixture.draft().environmentId(),
            fixture.draft().enabled(),
            fixture.draft().fallthroughVariationId(),
            fixture.draft().offVariationId(),
            fixture.draft().rolloutSalt(),
            fixture.draft().rules(),
            null,
            fixture.draft().changeSummary(),
            fixture.draft().version());
    EncodedSnapshot original =
        codec.encode(
            fixture.project(),
            fixture.environment(),
            List.of(fixture.flag()),
            List.of(noRollout),
            1,
            NOW);
    JsonNode originalFlag = objectMapper.readTree(original.canonicalJson()).path("flags");
    assertNull(originalFlag.path("new-checkout").get("rollout"));

    EncodedSnapshot rebased = codec.rebase(original.canonicalJson(), 2, NOW.plusSeconds(30));
    JsonNode rebasedRoot = objectMapper.readTree(rebased.canonicalJson());
    assertEquals(2L, rebasedRoot.path("revision").longValue());
    assertEquals(originalFlag, rebasedRoot.path("flags"));
    assertFalse(original.checksum().equals(rebased.checksum()));
  }

  @Test
  void canonicalJsonValidationRejectsDuplicateNamesUnsafeIntegersAndUnpairedUnicode() {
    assertEquals("{\"a\":\"é\",\"b\":1}", codec.canonicalizeJsonValue("{\"b\":1.0,\"a\":\"é\"}"));
    assertEquals("false", codec.canonicalizeJsonValue("false"));
    assertEquals("[1,2]", codec.canonicalizeJsonValue("[1.0,2]"));
    assertThrows(
        ControlPlaneRuleViolationException.class,
        () -> codec.canonicalizeJsonValue("{\"same\":1,\"same\":2}"));
    assertThrows(
        ControlPlaneRuleViolationException.class,
        () -> codec.canonicalizeJsonValue("{\"unsafe\":9007199254740992}"));
    assertThrows(
        ControlPlaneRuleViolationException.class,
        () -> codec.canonicalizeJsonValue("{\"unsafe\":9007199254740992.0}"));
    assertThrows(
        ControlPlaneRuleViolationException.class,
        () -> codec.canonicalizeJsonValue("{\"first\":true}{\"second\":false}"));
    assertThrows(
        ControlPlaneRuleViolationException.class,
        () -> codec.canonicalizeJsonValue("{\"bad\":\"\\ud800\"}"));
  }

  @Test
  void canonicalJsonLimitUsesCanonicalUtf8Bytes() throws Exception {
    String acceptedValue = "é".repeat(32_767);
    String acceptedJson = objectMapper.writeValueAsString(acceptedValue);
    String rejectedJson = objectMapper.writeValueAsString("é".repeat(32_768));

    assertEquals(FlagValue.MAX_VALUE_BYTES, acceptedJson.getBytes(StandardCharsets.UTF_8).length);
    assertEquals(acceptedJson, codec.canonicalizeJsonValue(acceptedJson));
    assertThrows(
        ControlPlaneRuleViolationException.class, () -> codec.canonicalizeJsonValue(rejectedJson));
    assertEquals("{}", codec.canonicalizeJsonValue("{" + " ".repeat(65_536) + "}"));
  }

  private static Fixture fixture() {
    ProjectId projectId = new ProjectId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    EnvironmentId environmentId =
        new EnvironmentId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    Project project =
        new Project(
            projectId,
            new OrganizationId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
            new ResourceKey("northstar.storefront"),
            "Northstar Storefront",
            null,
            Project.Status.ACTIVE,
            0,
            NOW,
            NOW);
    Environment environment =
        new Environment(
            environmentId,
            projectId,
            new ResourceKey("production"),
            "Production",
            Environment.Kind.PRODUCTION,
            Environment.Status.ACTIVE,
            41,
            7,
            NOW,
            NOW);
    UUID off = UUID.fromString("33333333-3333-3333-3333-333333333333");
    UUID on = UUID.fromString("44444444-4444-4444-4444-444444444444");
    FlagDefinition flag =
        new FlagDefinition(
            new FlagId(UUID.fromString("55555555-5555-5555-5555-555555555555")),
            projectId,
            new ResourceKey("new-checkout"),
            "New checkout",
            FlagType.BOOLEAN,
            true,
            List.of(
                new Variation(off, new ResourceKey("off"), "Off", FlagValue.bool(false)),
                new Variation(on, new ResourceKey("on"), "On", FlagValue.bool(true))),
            FlagDefinition.Status.ACTIVE,
            0,
            NOW,
            NOW);
    Rule rule =
        new Rule(
            UUID.fromString("66666666-6666-6666-6666-666666666666"),
            "Pro plans",
            List.of(new Condition("plan", AttributeType.STRING, Operator.EQUALS, List.of("pro"))),
            on);
    EnvironmentDraft draft =
        new EnvironmentDraft(
            flag.id(),
            environmentId,
            true,
            on,
            off,
            "stable_salt_123456",
            List.of(rule),
            new PercentageRollout(
                "userId", List.of(new Allocation(on, 10_000), new Allocation(off, 90_000))),
            "Enable the new checkout",
            3);
    return new Fixture(project, environment, flag, draft);
  }

  private record Fixture(
      Project project, Environment environment, FlagDefinition flag, EnvironmentDraft draft) {}
}
