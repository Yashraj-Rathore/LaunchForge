package dev.launchforge.domain.sdkkey;

import dev.launchforge.domain.controlplane.EnvironmentId;
import dev.launchforge.domain.controlplane.ProjectId;
import dev.launchforge.domain.organization.OrganizationId;
import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Public, environment-scoped browser identifier with exact-origin abuse controls. */
public record BrowserClientKey(
    SdkKeyId id,
    OrganizationId organizationId,
    ProjectId projectId,
    EnvironmentId environmentId,
    String name,
    String clientKey,
    String fingerprint,
    List<String> allowedOrigins,
    SdkKeyStatus status,
    Instant expiresAt,
    Instant createdAt,
    Instant lastUsedAt,
    Instant revokedAt) {
  public static final int MAXIMUM_ORIGINS = 20;

  public BrowserClientKey {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(organizationId, "organizationId");
    Objects.requireNonNull(projectId, "projectId");
    Objects.requireNonNull(environmentId, "environmentId");
    name = requireText(name, 120, "name");
    if (clientKey == null || !clientKey.matches("lf_client_[A-Za-z0-9_-]{32}")) {
      throw new IllegalArgumentException("clientKey is invalid");
    }
    fingerprint = requireText(fingerprint, 64, "fingerprint");
    allowedOrigins = validateOrigins(allowedOrigins);
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(createdAt, "createdAt");
    if (expiresAt != null && expiresAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("expiresAt must not precede createdAt");
    }
    if (revokedAt != null && revokedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("revokedAt must not precede createdAt");
    }
  }

  public boolean usableAt(Instant instant) {
    Objects.requireNonNull(instant, "instant");
    return status == SdkKeyStatus.ACTIVE && (expiresAt == null || expiresAt.isAfter(instant));
  }

  private static List<String> validateOrigins(List<String> values) {
    if (values == null || values.isEmpty() || values.size() > MAXIMUM_ORIGINS) {
      throw new IllegalArgumentException("allowedOrigins must contain between one and 20 origins");
    }
    Set<String> unique = new HashSet<>();
    for (String value : values) {
      String origin = requireText(value, 300, "allowedOrigin");
      URI uri;
      try {
        uri = URI.create(origin);
      } catch (IllegalArgumentException exception) {
        throw new IllegalArgumentException("allowedOrigin is invalid", exception);
      }
      String scheme = uri.getScheme();
      String host = uri.getHost();
      boolean localHttp =
          "http".equals(scheme) && ("localhost".equals(host) || "127.0.0.1".equals(host));
      if (scheme == null
          || host == null
          || (!"https".equals(scheme) && !localHttp)
          || !host.equals(host.toLowerCase(Locale.ROOT))
          || uri.getRawUserInfo() != null
          || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null
          || !origin.equals(canonicalOrigin(uri))) {
        throw new IllegalArgumentException("allowedOrigin must be an exact HTTPS origin");
      }
      if (!unique.add(origin)) {
        throw new IllegalArgumentException("allowedOrigins must be unique");
      }
    }
    return List.copyOf(values);
  }

  private static String canonicalOrigin(URI uri) {
    return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
  }

  private static String requireText(String value, int maximum, String field) {
    if (value == null
        || value.isBlank()
        || !value.equals(value.strip())
        || value.length() > maximum) {
      throw new IllegalArgumentException(field + " is invalid");
    }
    return value;
  }
}
