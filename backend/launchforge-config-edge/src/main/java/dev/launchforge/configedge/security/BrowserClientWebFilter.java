package dev.launchforge.configedge.security;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public final class BrowserClientWebFilter implements WebFilter {
  public static final String SCOPE_ATTRIBUTE = BrowserClientScope.class.getName();
  private static final String PREFIX = "/sdk/v1/client/";
  private static final Set<String> ALLOWED_REQUEST_HEADERS =
      Set.of("accept", "if-none-match", "last-event-id");
  private static final String EXPOSED_HEADERS =
      "ETag, X-LaunchForge-Revision, X-LaunchForge-Checksum, X-LaunchForge-Schema-Version";

  private final BrowserClientAuthenticationService authenticationService;

  public BrowserClientWebFilter(BrowserClientAuthenticationService authenticationService) {
    this.authenticationService = authenticationService;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    BrowserPath browserPath = path(exchange.getRequest().getPath().value());
    if (browserPath == null) {
      return chain.filter(exchange);
    }
    String origin = exchange.getRequest().getHeaders().getOrigin();
    return Mono.fromCallable(
            () -> authenticationService.authenticate(browserPath.clientKey(), origin))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(
            scope -> {
              if (origin != null) {
                addCorsHeaders(exchange, origin);
              }
              if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
                if (origin == null || !validPreflight(exchange)) {
                  return problem(exchange, HttpStatus.FORBIDDEN, "CLIENT_CORS_DENIED");
                }
                exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
                return exchange.getResponse().setComplete();
              }
              if (exchange.getRequest().getMethod() != HttpMethod.GET) {
                return problem(exchange, HttpStatus.METHOD_NOT_ALLOWED, "CLIENT_METHOD_DENIED");
              }
              exchange.getAttributes().put(SCOPE_ATTRIBUTE, scope);
              return chain.filter(exchange);
            })
        .onErrorResume(
            BrowserClientAuthenticationException.class,
            exception ->
                problem(
                    exchange,
                    exception.isForbidden() ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED,
                    exception.isForbidden() ? "CLIENT_ORIGIN_DENIED" : "CLIENT_KEY_INVALID"))
        .onErrorResume(
            DataAccessException.class,
            exception ->
                problem(exchange, HttpStatus.SERVICE_UNAVAILABLE, "EDGE_DATABASE_UNAVAILABLE"));
  }

  private static BrowserPath path(String value) {
    if (!value.startsWith(PREFIX)) {
      return null;
    }
    String[] parts = value.substring(PREFIX.length()).split("/", -1);
    if (parts.length != 2 || !("snapshot".equals(parts[1]) || "stream".equals(parts[1]))) {
      return null;
    }
    return new BrowserPath(parts[0], parts[1]);
  }

  private static boolean validPreflight(ServerWebExchange exchange) {
    String method = exchange.getRequest().getHeaders().getFirst("Access-Control-Request-Method");
    if (!"GET".equals(method)) {
      return false;
    }
    String requested =
        exchange.getRequest().getHeaders().getFirst("Access-Control-Request-Headers");
    if (requested == null || requested.isBlank()) {
      return true;
    }
    Set<String> headers =
        Arrays.stream(requested.split(","))
            .map(String::strip)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
    return ALLOWED_REQUEST_HEADERS.containsAll(headers);
  }

  private static void addCorsHeaders(ServerWebExchange exchange, String origin) {
    HttpHeaders headers = exchange.getResponse().getHeaders();
    headers.setAccessControlAllowOrigin(origin);
    headers.setAccessControlAllowMethods(java.util.List.of(HttpMethod.GET));
    headers.setAccessControlAllowHeaders(
        java.util.List.of(HttpHeaders.ACCEPT, HttpHeaders.IF_NONE_MATCH, "Last-Event-ID"));
    headers.setAccessControlExposeHeaders(Arrays.asList(EXPOSED_HEADERS.split(", ")));
    headers.setAccessControlMaxAge(600);
    headers.add(HttpHeaders.VARY, HttpHeaders.ORIGIN);
    headers.add(HttpHeaders.VARY, "Access-Control-Request-Method");
    headers.add(HttpHeaders.VARY, "Access-Control-Request-Headers");
  }

  private static Mono<Void> problem(ServerWebExchange exchange, HttpStatus status, String code) {
    exchange.getResponse().setStatusCode(status);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    String body =
        "{\"type\":\"https://launchforge.dev/problems/"
            + code.toLowerCase(Locale.ROOT)
            + "\",\"title\":\""
            + status.getReasonPhrase()
            + "\",\"status\":"
            + status.value()
            + ",\"code\":\""
            + code
            + "\",\"correlationId\":\""
            + UUID.randomUUID()
            + "\"}";
    DataBuffer buffer =
        exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }

  private record BrowserPath(String clientKey, String resource) {}
}
