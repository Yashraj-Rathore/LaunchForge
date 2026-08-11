package dev.launchforge.domain.controlplane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.launchforge.domain.controlplane.FlagDefinition.FlagType;
import dev.launchforge.domain.controlplane.FlagDefinition.FlagValue;
import dev.launchforge.domain.controlplane.FlagDefinition.Status;
import dev.launchforge.domain.controlplane.FlagDefinition.Variation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FlagDefinitionTest {
  private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

  @Test
  void acceptsTypedUniqueVariationsAndNormalizesNumbers() {
    FlagValue value = FlagValue.number(new BigDecimal("10.5000"));
    assertEquals("10.5", value.canonicalValue());

    FlagDefinition flag =
        flag(
            FlagType.NUMBER,
            List.of(
                variation("small", value), variation("large", FlagValue.number(BigDecimal.TEN))));
    assertEquals(2, flag.variations().size());
  }

  @Test
  void rejectsVariationTypeMismatchDuplicateKeysAndUnsafeIntegers() {
    assertThrows(
        ControlPlaneRuleViolationException.class,
        () ->
            flag(
                FlagType.BOOLEAN,
                List.of(
                    variation("on", FlagValue.bool(true)),
                    variation("off", FlagValue.string("false")))));
    assertThrows(
        ControlPlaneRuleViolationException.class,
        () ->
            flag(
                FlagType.BOOLEAN,
                List.of(
                    variation("same", FlagValue.bool(true)),
                    variation("same", FlagValue.bool(false)))));
    assertThrows(
        ControlPlaneRuleViolationException.class,
        () -> FlagValue.number(new BigDecimal("9007199254740992")));
    assertThrows(
        ControlPlaneRuleViolationException.class,
        () -> FlagValue.number(new BigDecimal("9007199254740992.0")));
  }

  @Test
  void rejectsFlagsOutsideProductVariationBounds() {
    assertThrows(
        ControlPlaneRuleViolationException.class,
        () -> flag(FlagType.BOOLEAN, List.of(variation("only", FlagValue.bool(true)))));
  }

  private static FlagDefinition flag(FlagType type, List<Variation> variations) {
    return new FlagDefinition(
        FlagId.random(),
        ProjectId.random(),
        new ResourceKey("checkout.enabled"),
        "Checkout enabled",
        type,
        false,
        variations,
        Status.ACTIVE,
        0,
        NOW,
        NOW);
  }

  private static Variation variation(String key, FlagValue value) {
    return new Variation(UUID.randomUUID(), new ResourceKey(key), key, value);
  }
}
