package dev.launchforge.configedge.security;

import dev.launchforge.configedge.observability.EdgeAuthenticationMetrics;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class SdkAuthenticationWebFilter implements WebFilter {
  public static final String SCOPE_ATTRIBUTE = SdkCredentialScope.class.getName();

  private final SdkAuthenticationService authenticationService;
  private final EdgeAuthenticationMetrics metrics;

  public SdkAuthenticationWebFilter(
      SdkAuthenticationService authenticationService, EdgeAuthenticationMetrics metrics) {
    this.authenticationService = authenticationService;
    this.metrics = metrics;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String path = exchange.getRequest().getPath().value();
    if (!("/sdk/v1/snapshot".equals(path)
        || "/sdk/v1/stream".equals(path)
        || "/events/v1/evaluations/batch".equals(path))) {
      return chain.filter(exchange);
    }
    String authorization = oneAuthorizationValue(exchange.getRequest().getHeaders());
    return Mono.fromCallable(() -> authenticationService.authenticate(authorization))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(
            scope -> {
              exchange.getAttributes().put(SCOPE_ATTRIBUTE, scope);
              return chain.filter(exchange);
            })
        .onErrorResume(
            SdkAuthenticationException.class,
            exception -> {
              metrics.denied("server", route(path));
              return problem(
                  exchange,
                  exception.isForbidden() ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED,
                  exception.isForbidden() ? "SDK_SCOPE_INACTIVE" : "SDK_KEY_INVALID");
            })
        .onErrorResume(
            DataAccessException.class,
            exception ->
                problem(exchange, HttpStatus.SERVICE_UNAVAILABLE, "EDGE_DATABASE_UNAVAILABLE"));
  }

  private static String oneAuthorizationValue(HttpHeaders headers) {
    List<String> values = headers.get(HttpHeaders.AUTHORIZATION);
    return values == null || values.size() != 1 ? null : values.getFirst();
  }

  private static String route(String path) {
    if (path.endsWith("/stream")) {
      return "stream";
    }
    return path.contains("/evaluations/") ? "analytics" : "snapshot";
  }

  private static Mono<Void> problem(ServerWebExchange exchange, HttpStatus status, String code) {
    exchange.getResponse().setStatusCode(status);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    String body =
        "{\"type\":\"https://launchforge.dev/problems/"
            + code.toLowerCase(java.util.Locale.ROOT)
            + "\",\"title\":\""
            + status.getReasonPhrase()
            + "\",\"status\":"
            + status.value()
            + ",\"code\":\""
            + code
            + "\",\"correlationId\":\""
            + EdgeCorrelationWebFilter.get(exchange)
            + "\"}";
    DataBuffer buffer =
        exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }
}
