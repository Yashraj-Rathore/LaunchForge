package dev.launchforge.controlapi.simulation;

import dev.launchforge.controlapi.security.OperatorIdentityResolver;
import dev.launchforge.controlapi.simulation.EvaluationSimulationService.SimulationResult;
import dev.launchforge.domain.controlplane.EnvironmentId;
import dev.launchforge.domain.controlplane.FlagDefinition.FlagType;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/environments/{environmentId}/evaluate")
public final class EvaluationSimulationController {
  private final OperatorIdentityResolver identityResolver;
  private final EvaluationSimulationService service;

  public EvaluationSimulationController(
      OperatorIdentityResolver identityResolver, EvaluationSimulationService service) {
    this.identityResolver = identityResolver;
    this.service = service;
  }

  @PostMapping
  public SimulationResult simulate(
      Authentication authentication,
      @PathVariable UUID environmentId,
      @RequestBody SimulationRequest request) {
    if (request.context() == null) {
      throw new IllegalArgumentException("Simulation context is required");
    }
    return service.simulate(
        identityResolver.requireIdentity(authentication),
        new EnvironmentId(environmentId),
        request.flagKey(),
        request.type(),
        request.defaultValue(),
        request.context().key(),
        request.context().attributes());
  }

  public record SimulationRequest(
      String flagKey, FlagType type, JsonNode defaultValue, SimulationContext context) {}

  public record SimulationContext(String key, Map<String, JsonNode> attributes) {}
}
