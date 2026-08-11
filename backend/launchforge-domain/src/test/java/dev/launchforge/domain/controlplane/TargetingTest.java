package dev.launchforge.domain.controlplane;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.launchforge.domain.controlplane.Targeting.Allocation;
import dev.launchforge.domain.controlplane.Targeting.AttributeType;
import dev.launchforge.domain.controlplane.Targeting.Condition;
import dev.launchforge.domain.controlplane.Targeting.Operator;
import dev.launchforge.domain.controlplane.Targeting.PercentageRollout;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TargetingTest {
  @Test
  void operatorMatrixIsExplicitAndRejectsImplicitCoercion() {
    assertTrue(Operator.CONTAINS.supports(AttributeType.STRING));
    assertFalse(Operator.CONTAINS.supports(AttributeType.NUMBER));
    assertTrue(Operator.EXISTS.supports(AttributeType.BOOLEAN));

    assertDoesNotThrow(
        () -> new Condition("plan", AttributeType.STRING, Operator.IN, List.of("pro", "team")));
    assertThrows(
        ControlPlaneRuleViolationException.class,
        () -> new Condition("age", AttributeType.NUMBER, Operator.CONTAINS, List.of("10")));
    assertThrows(
        ControlPlaneRuleViolationException.class,
        () -> new Condition("age", AttributeType.NUMBER, Operator.GT, List.of("ten")));
    assertThrows(
        ControlPlaneRuleViolationException.class,
        () ->
            new Condition("age", AttributeType.NUMBER, Operator.GT, List.of("9007199254740992.0")));
    assertThrows(
        ControlPlaneRuleViolationException.class,
        () -> new Condition("version", AttributeType.SEMVER, Operator.SEMVER_GTE, List.of("v1")));
  }

  @Test
  void percentageRolloutRequiresPositiveIntegerWeightsTotallingOneHundredThousand() {
    UUID on = UUID.randomUUID();
    UUID off = UUID.randomUUID();
    assertDoesNotThrow(
        () ->
            new PercentageRollout(
                "userId", List.of(new Allocation(on, 10_000), new Allocation(off, 90_000))));

    assertThrows(
        ControlPlaneRuleViolationException.class,
        () ->
            new PercentageRollout(
                "userId", List.of(new Allocation(on, 9_999), new Allocation(off, 90_000))));
    assertThrows(
        ControlPlaneRuleViolationException.class,
        () ->
            new PercentageRollout(
                "userId", List.of(new Allocation(on, 50_000), new Allocation(on, 50_000))));
  }
}
