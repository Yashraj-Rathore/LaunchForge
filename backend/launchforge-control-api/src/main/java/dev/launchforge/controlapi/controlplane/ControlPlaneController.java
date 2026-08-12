package dev.launchforge.controlapi.controlplane;

import dev.launchforge.application.controlplane.ControlPlaneService;
import dev.launchforge.application.controlplane.ControlPlaneService.DraftInput;
import dev.launchforge.application.controlplane.ControlPlaneService.VariationInput;
import dev.launchforge.application.controlplane.ControlPlaneService.VariationUpdateInput;
import dev.launchforge.application.controlplane.PublishedRevision;
import dev.launchforge.application.controlplane.SnapshotCodec.RevisionDiff;
import dev.launchforge.controlapi.security.OperatorIdentityResolver;
import dev.launchforge.controlapi.web.PreconditionRequiredException;
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
import dev.launchforge.domain.controlplane.Targeting.Allocation;
import dev.launchforge.domain.controlplane.Targeting.PercentageRollout;
import dev.launchforge.domain.controlplane.Targeting.Rule;
import dev.launchforge.domain.organization.OidcIdentity;
import dev.launchforge.domain.organization.OrganizationId;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1")
public final class ControlPlaneController {
  private final OperatorIdentityResolver identityResolver;
  private final ControlPlaneService service;
  private final ObjectMapper objectMapper;

  public ControlPlaneController(
      OperatorIdentityResolver identityResolver,
      ControlPlaneService service,
      ObjectMapper objectMapper) {
    this.identityResolver = identityResolver;
    this.service = service;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/organizations/{organizationId}/projects")
  public ResponseEntity<ProjectResponse> createProject(
      Authentication authentication,
      @PathVariable UUID organizationId,
      @RequestBody CreateProjectRequest request) {
    Project project =
        service.createProject(
            actor(authentication),
            new OrganizationId(organizationId),
            request.key(),
            request.name(),
            request.description());
    return created(ProjectResponse.from(project), project.version());
  }

  @GetMapping("/organizations/{organizationId}/projects")
  public List<ProjectResponse> projects(
      Authentication authentication, @PathVariable UUID organizationId) {
    return service.listProjects(actor(authentication), new OrganizationId(organizationId)).stream()
        .map(ProjectResponse::from)
        .toList();
  }

  @PatchMapping("/projects/{projectId}")
  public ResponseEntity<ProjectResponse> updateProject(
      Authentication authentication,
      @PathVariable UUID projectId,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestBody UpdateProjectRequest request) {
    Project project =
        service.updateProject(
            actor(authentication),
            new ProjectId(projectId),
            request.name(),
            request.description(),
            request.status(),
            expectedVersion(ifMatch));
    return versioned(ProjectResponse.from(project), project.version());
  }

  @PostMapping("/projects/{projectId}/environments")
  public ResponseEntity<EnvironmentResponse> createEnvironment(
      Authentication authentication,
      @PathVariable UUID projectId,
      @RequestBody CreateEnvironmentRequest request) {
    Environment environment =
        service.createEnvironment(
            actor(authentication),
            new ProjectId(projectId),
            request.key(),
            request.name(),
            request.kind());
    return created(EnvironmentResponse.from(environment), environment.version());
  }

  @GetMapping("/projects/{projectId}/environments")
  public List<EnvironmentResponse> environments(
      Authentication authentication, @PathVariable UUID projectId) {
    return service.listEnvironments(actor(authentication), new ProjectId(projectId)).stream()
        .map(EnvironmentResponse::from)
        .toList();
  }

  @PatchMapping("/environments/{environmentId}")
  public ResponseEntity<EnvironmentResponse> updateEnvironment(
      Authentication authentication,
      @PathVariable UUID environmentId,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestBody UpdateEnvironmentRequest request) {
    Environment environment =
        service.updateEnvironment(
            actor(authentication),
            new EnvironmentId(environmentId),
            request.name(),
            request.status(),
            expectedVersion(ifMatch));
    return versioned(EnvironmentResponse.from(environment), environment.version());
  }

  @PostMapping("/projects/{projectId}/flags")
  public ResponseEntity<FlagResponse> createFlag(
      Authentication authentication,
      @PathVariable UUID projectId,
      @RequestBody CreateFlagRequest request) {
    List<VariationInput> inputs =
        request.variations().stream()
            .map(
                variation ->
                    new VariationInput(
                        variation.key(),
                        variation.name(),
                        typedValue(request.type(), variation.value())))
            .toList();
    FlagDefinition flag =
        service.createFlag(
            actor(authentication),
            new ProjectId(projectId),
            request.key(),
            request.name(),
            request.type(),
            request.clientVisible(),
            inputs);
    return created(FlagResponse.from(flag, this), flag.version());
  }

  @GetMapping("/projects/{projectId}/flags")
  public List<FlagResponse> flags(Authentication authentication, @PathVariable UUID projectId) {
    return service.listFlags(actor(authentication), new ProjectId(projectId)).stream()
        .map(flag -> FlagResponse.from(flag, this))
        .toList();
  }

  @GetMapping("/flags/{flagId}")
  public ResponseEntity<FlagResponse> flag(
      Authentication authentication, @PathVariable UUID flagId) {
    FlagDefinition flag = service.getFlag(actor(authentication), new FlagId(flagId));
    return versioned(FlagResponse.from(flag, this), flag.version());
  }

  @PatchMapping("/flags/{flagId}")
  public ResponseEntity<FlagResponse> updateFlag(
      Authentication authentication,
      @PathVariable UUID flagId,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestBody UpdateFlagRequest request) {
    OidcIdentity actor = actor(authentication);
    FlagType type =
        request.variations() == null ? null : service.getFlag(actor, new FlagId(flagId)).type();
    FlagDefinition flag =
        service.updateFlag(
            actor,
            new FlagId(flagId),
            request.name(),
            request.status(),
            request.variations() == null
                ? null
                : request.variations().stream()
                    .map(
                        variation ->
                            new VariationUpdateInput(
                                variation.id(),
                                variation.name(),
                                typedValue(type, variation.value())))
                    .toList(),
            expectedVersion(ifMatch));
    return versioned(FlagResponse.from(flag, this), flag.version());
  }

  @PostMapping("/flags/{flagId}/archive")
  public ResponseEntity<FlagResponse> archiveFlag(
      Authentication authentication,
      @PathVariable UUID flagId,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
    FlagDefinition current = service.getFlag(actor(authentication), new FlagId(flagId));
    FlagDefinition flag =
        service.updateFlag(
            actor(authentication),
            new FlagId(flagId),
            current.name(),
            FlagDefinition.Status.ARCHIVED,
            expectedVersion(ifMatch));
    return versioned(FlagResponse.from(flag, this), flag.version());
  }

  @GetMapping("/flags/{flagId}/environments/{environmentId}")
  public ResponseEntity<DraftResponse> draft(
      Authentication authentication, @PathVariable UUID flagId, @PathVariable UUID environmentId) {
    EnvironmentDraft draft =
        service.getDraft(
            actor(authentication), new FlagId(flagId), new EnvironmentId(environmentId));
    return versioned(DraftResponse.from(draft), draft.version());
  }

  @PutMapping("/flags/{flagId}/environments/{environmentId}")
  public ResponseEntity<DraftResponse> updateDraft(
      Authentication authentication,
      @PathVariable UUID flagId,
      @PathVariable UUID environmentId,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestBody DraftRequest request) {
    PercentageRollout rollout =
        request.rollout() == null
            ? null
            : new PercentageRollout(
                request.rollout().subjectAttribute(), request.rollout().allocations());
    EnvironmentDraft draft =
        service.updateDraft(
            actor(authentication),
            new FlagId(flagId),
            new EnvironmentId(environmentId),
            new DraftInput(
                request.enabled(),
                request.fallthroughVariationId(),
                request.offVariationId(),
                request.rules(),
                rollout,
                request.changeSummary()),
            expectedVersion(ifMatch));
    return versioned(DraftResponse.from(draft), draft.version());
  }

  @PostMapping("/flags/{flagId}/environments/{environmentId}/rollout/reseed")
  public ResponseEntity<DraftResponse> reseedRollout(
      Authentication authentication,
      @PathVariable UUID flagId,
      @PathVariable UUID environmentId,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestBody ReasonRequest request) {
    EnvironmentDraft draft =
        service.reseedRollout(
            actor(authentication),
            new FlagId(flagId),
            new EnvironmentId(environmentId),
            expectedVersion(ifMatch),
            request.reason());
    return versioned(DraftResponse.from(draft), draft.version());
  }

  @PostMapping("/environments/{environmentId}/publish")
  public ResponseEntity<RevisionResponse> publish(
      Authentication authentication,
      @PathVariable UUID environmentId,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestBody(required = false) ReasonRequest request) {
    PublishedRevision revision =
        service.publish(
            actor(authentication),
            new EnvironmentId(environmentId),
            expectedVersion(ifMatch),
            request == null ? null : request.reason());
    return ResponseEntity.status(HttpStatus.CREATED)
        .eTag(Long.toString(revision.revision()))
        .body(RevisionResponse.full(revision));
  }

  @GetMapping("/environments/{environmentId}/revisions")
  public List<RevisionResponse> revisions(
      Authentication authentication, @PathVariable UUID environmentId) {
    return service.revisionHistory(actor(authentication), new EnvironmentId(environmentId)).stream()
        .map(RevisionResponse::summary)
        .toList();
  }

  @GetMapping("/environments/{environmentId}/revisions/{revision}")
  public ResponseEntity<RevisionResponse> revision(
      Authentication authentication,
      @PathVariable UUID environmentId,
      @PathVariable long revision) {
    PublishedRevision result =
        service.revision(actor(authentication), new EnvironmentId(environmentId), revision);
    return ResponseEntity.ok().eTag(Long.toString(revision)).body(RevisionResponse.full(result));
  }

  @GetMapping("/environments/{environmentId}/revisions/diff")
  public RevisionDiff diff(
      Authentication authentication,
      @PathVariable UUID environmentId,
      @RequestParam long from,
      @RequestParam long to) {
    return service.diff(actor(authentication), new EnvironmentId(environmentId), from, to);
  }

  @PostMapping("/environments/{environmentId}/rollback")
  public ResponseEntity<RevisionResponse> rollback(
      Authentication authentication,
      @PathVariable UUID environmentId,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestBody RollbackRequest request) {
    PublishedRevision revision =
        service.rollback(
            actor(authentication),
            new EnvironmentId(environmentId),
            request.sourceRevision(),
            expectedVersion(ifMatch),
            request.reason());
    return ResponseEntity.status(HttpStatus.CREATED)
        .eTag(Long.toString(revision.revision()))
        .body(RevisionResponse.full(revision));
  }

  private OidcIdentity actor(Authentication authentication) {
    return identityResolver.requireIdentity(authentication);
  }

  private FlagValue typedValue(FlagType type, JsonNode value) {
    if (value == null) {
      throw new ControlPlaneRuleViolationException("Variation value is required");
    }
    return switch (type) {
      case BOOLEAN -> {
        if (!value.isBoolean()) {
          throw new ControlPlaneRuleViolationException("Boolean flag requires boolean values");
        }
        yield FlagValue.bool(value.booleanValue());
      }
      case STRING -> {
        if (!value.isString()) {
          throw new ControlPlaneRuleViolationException("String flag requires string values");
        }
        yield FlagValue.string(value.asString());
      }
      case NUMBER -> {
        if (!value.isNumber()) {
          throw new ControlPlaneRuleViolationException("Number flag requires number values");
        }
        yield FlagValue.number(value.decimalValue());
      }
      case JSON -> FlagValue.json(service.canonicalizeJsonValue(value.toString()));
    };
  }

  private JsonNode valueNode(FlagValue value) {
    try {
      return switch (value.type()) {
        case BOOLEAN -> objectMapper.readTree(value.canonicalValue());
        case STRING -> objectMapper.valueToTree(value.canonicalValue());
        case NUMBER -> objectMapper.valueToTree(new BigDecimal(value.canonicalValue()));
        case JSON -> objectMapper.readTree(value.canonicalValue());
      };
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored variation value cannot be rendered", exception);
    }
  }

  private static long expectedVersion(String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new PreconditionRequiredException();
    }
    String value = ifMatch.strip();
    if (value.startsWith("W/")) {
      value = value.substring(2);
    }
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      value = value.substring(1, value.length() - 1);
    }
    try {
      long version = Long.parseLong(value);
      if (version < 0) {
        throw new PreconditionRequiredException();
      }
      return version;
    } catch (NumberFormatException exception) {
      throw new PreconditionRequiredException();
    }
  }

  private static <T> ResponseEntity<T> created(T body, long version) {
    return ResponseEntity.status(HttpStatus.CREATED).eTag(Long.toString(version)).body(body);
  }

  private static <T> ResponseEntity<T> versioned(T body, long version) {
    return ResponseEntity.ok().eTag(Long.toString(version)).body(body);
  }

  public record CreateProjectRequest(String key, String name, String description) {}

  public record UpdateProjectRequest(String name, String description, Project.Status status) {}

  public record CreateEnvironmentRequest(String key, String name, Environment.Kind kind) {}

  public record UpdateEnvironmentRequest(String name, Environment.Status status) {}

  public record CreateFlagRequest(
      String key,
      String name,
      FlagType type,
      boolean clientVisible,
      List<VariationRequest> variations) {}

  public record VariationRequest(String key, String name, JsonNode value) {}

  public record UpdateFlagRequest(
      String name, FlagDefinition.Status status, List<UpdateVariationRequest> variations) {}

  public record UpdateVariationRequest(UUID id, String name, JsonNode value) {}

  public record DraftRequest(
      boolean enabled,
      UUID fallthroughVariationId,
      UUID offVariationId,
      List<Rule> rules,
      RolloutRequest rollout,
      String changeSummary) {}

  public record RolloutRequest(String subjectAttribute, List<Allocation> allocations) {}

  public record ReasonRequest(String reason) {}

  public record RollbackRequest(long sourceRevision, String reason) {}

  public record ProjectResponse(
      String id,
      String organizationId,
      String key,
      String name,
      String description,
      String status,
      long version) {
    static ProjectResponse from(Project project) {
      return new ProjectResponse(
          project.id().value().toString(),
          project.organizationId().value().toString(),
          project.key().value(),
          project.name(),
          project.description(),
          project.status().name(),
          project.version());
    }
  }

  public record EnvironmentResponse(
      String id,
      String projectId,
      String key,
      String name,
      String kind,
      String status,
      long currentRevision,
      long version) {
    static EnvironmentResponse from(Environment environment) {
      return new EnvironmentResponse(
          environment.id().value().toString(),
          environment.projectId().value().toString(),
          environment.key().value(),
          environment.name(),
          environment.kind().name(),
          environment.status().name(),
          environment.currentRevision(),
          environment.version());
    }
  }

  public record VariationResponse(String id, String key, String name, JsonNode value) {
    static VariationResponse from(Variation variation, ControlPlaneController controller) {
      return new VariationResponse(
          variation.id().toString(),
          variation.key().value(),
          variation.name(),
          controller.valueNode(variation.value()));
    }
  }

  public record FlagResponse(
      String id,
      String projectId,
      String key,
      String name,
      String type,
      boolean clientVisible,
      String status,
      long version,
      List<VariationResponse> variations) {
    static FlagResponse from(FlagDefinition flag, ControlPlaneController controller) {
      return new FlagResponse(
          flag.id().value().toString(),
          flag.projectId().value().toString(),
          flag.key().value(),
          flag.name(),
          flag.type().name(),
          flag.clientVisible(),
          flag.status().name(),
          flag.version(),
          flag.variations().stream()
              .map(variation -> VariationResponse.from(variation, controller))
              .toList());
    }
  }

  public record DraftResponse(
      String flagId,
      String environmentId,
      boolean enabled,
      String fallthroughVariationId,
      String offVariationId,
      String rolloutSalt,
      List<Rule> rules,
      PercentageRollout rollout,
      String changeSummary,
      long version) {
    static DraftResponse from(EnvironmentDraft draft) {
      return new DraftResponse(
          draft.flagId().value().toString(),
          draft.environmentId().value().toString(),
          draft.enabled(),
          draft.fallthroughVariationId().toString(),
          draft.offVariationId().toString(),
          draft.rolloutSalt(),
          draft.rules(),
          draft.rollout(),
          draft.changeSummary(),
          draft.version());
    }
  }

  public record RevisionResponse(
      String environmentId,
      long revision,
      Long sourceRevision,
      String checksum,
      String snapshot,
      String reason,
      String actorSubject,
      String createdAt) {
    static RevisionResponse summary(PublishedRevision revision) {
      return from(revision, null);
    }

    static RevisionResponse full(PublishedRevision revision) {
      return from(revision, revision.canonicalSnapshot());
    }

    private static RevisionResponse from(PublishedRevision revision, String snapshot) {
      return new RevisionResponse(
          revision.environmentId().value().toString(),
          revision.revision(),
          revision.sourceRevision(),
          revision.checksum(),
          snapshot,
          revision.reason(),
          revision.actorSubject(),
          revision.createdAt().toString());
    }
  }
}
