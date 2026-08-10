package dev.launchforge.domain.controlplane;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.launchforge.domain.controlplane.FlagDefinition.FlagType;
import dev.launchforge.domain.controlplane.FlagDefinition.FlagValue;
import dev.launchforge.domain.controlplane.FlagDefinition.Status;
import dev.launchforge.domain.controlplane.FlagDefinition.Variation;
import dev.launchforge.domain.controlplane.Targeting.AttributeType;
import dev.launchforge.domain.controlplane.Targeting.Condition;
import dev.launchforge.domain.controlplane.Targeting.Operator;
import dev.launchforge.domain.controlplane.Targeting.Rule;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnvironmentDraftTest {
  @Test
  void validatesOrderedRulesAndAllVariationReferences() {
    UUID off = UUID.randomUUID();
    UUID on = UUID.randomUUID();
    FlagDefinition flag = flag(off, on);
    EnvironmentDraft valid =
        new EnvironmentDraft(
            flag.id(),
            EnvironmentId.random(),
            true,
            on,
            off,
            "stable_salt_123456",
            List.of(
                new Rule(
                    UUID.randomUUID(),
                    "Pro accounts",
                    List.of(
                        new Condition(
                            "plan", AttributeType.STRING, Operator.EQUALS, List.of("pro"))),
                    on)),
            null,
            "Enable checkout for pro accounts",
            0);
    assertDoesNotThrow(() -> valid.validateFor(flag));

    EnvironmentDraft invalid =
        new EnvironmentDraft(
            flag.id(),
            EnvironmentId.random(),
            true,
            UUID.randomUUID(),
            off,
            "stable_salt_123456",
            List.of(),
            null,
            "Invalid reference",
            0);
    assertThrows(ControlPlaneRuleViolationException.class, () -> invalid.validateFor(flag));
  }

  private static FlagDefinition flag(UUID off, UUID on) {
    Instant now = Instant.parse("2026-08-10T12:00:00Z");
    return new FlagDefinition(
        FlagId.random(),
        ProjectId.random(),
        new ResourceKey("checkout.enabled"),
        "Checkout enabled",
        FlagType.BOOLEAN,
        false,
        List.of(
            new Variation(off, new ResourceKey("off"), "Off", FlagValue.bool(false)),
            new Variation(on, new ResourceKey("on"), "On", FlagValue.bool(true))),
        Status.ACTIVE,
        0,
        now,
        now);
  }
}
