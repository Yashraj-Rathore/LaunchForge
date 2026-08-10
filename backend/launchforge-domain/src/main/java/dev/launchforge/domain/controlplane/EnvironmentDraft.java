package dev.launchforge.domain.controlplane;

import dev.launchforge.domain.controlplane.Targeting.PercentageRollout;
import dev.launchforge.domain.controlplane.Targeting.Rule;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record EnvironmentDraft(
    FlagId flagId,
    EnvironmentId environmentId,
    boolean enabled,
    UUID fallthroughVariationId,
    UUID offVariationId,
    String rolloutSalt,
    List<Rule> rules,
    PercentageRollout rollout,
    String changeSummary,
    long version) {
  public EnvironmentDraft {
    Objects.requireNonNull(flagId, "flagId");
    Objects.requireNonNull(environmentId, "environmentId");
    Objects.requireNonNull(fallthroughVariationId, "fallthroughVariationId");
    Objects.requireNonNull(offVariationId, "offVariationId");
    if (rolloutSalt == null
        || rolloutSalt.length() < 16
        || rolloutSalt.length() > 128
        || !rolloutSalt.matches("^[A-Za-z0-9_-]+$")) {
      throw new ControlPlaneRuleViolationException("Rollout salt is invalid");
    }
    rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    if (rules.size() > Targeting.MAX_RULES) {
      throw new ControlPlaneRuleViolationException("Draft has too many rules");
    }
    Set<UUID> ruleIds = new HashSet<>();
    for (Rule rule : rules) {
      if (!ruleIds.add(rule.id())) {
        throw new ControlPlaneRuleViolationException("Rule IDs must be unique");
      }
    }
    if (changeSummary == null
        || changeSummary.isBlank()
        || !changeSummary.equals(changeSummary.strip())
        || changeSummary.length() > 500) {
      throw new ControlPlaneRuleViolationException("Change summary is invalid");
    }
    if (version < 0) {
      throw new ControlPlaneRuleViolationException("Draft version cannot be negative");
    }
  }

  public void validateFor(FlagDefinition flag) {
    Objects.requireNonNull(flag, "flag");
    if (!flag.id().equals(flagId)) {
      throw new ControlPlaneRuleViolationException("Draft does not belong to flag");
    }
    requireVariation(flag, fallthroughVariationId);
    requireVariation(flag, offVariationId);
    for (Rule rule : rules) {
      requireVariation(flag, rule.variationId());
    }
    if (rollout != null) {
      rollout.allocations().forEach(allocation -> requireVariation(flag, allocation.variationId()));
    }
  }

  private static void requireVariation(FlagDefinition flag, UUID variationId) {
    if (!flag.hasVariation(variationId)) {
      throw new ControlPlaneRuleViolationException("Draft references an unknown variation");
    }
  }
}
