package dev.launchforge.configedge.security;

import dev.launchforge.configedge.security.EdgeRateLimiter.Policy;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
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
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class EdgeRateLimitWebFilter implements WebFilter {
  private final EdgeRateLimiter limiter;

  public EdgeRateLimitWebFilter(EdgeRateLimiter limiter) {
    this.limiter = limiter;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    Policy policy = policy(exchange.getRequest().getPath().value());
    UUID keyId = keyId(exchange);
    if (policy == null || keyId == null) {
      return chain.filter(exchange);
    }
    return Mono.fromCallable(() -> limiter.check(policy, keyId))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(
            decision -> {
              if (decision.permitted()) {
                return chain.filter(exchange);
              }
              exchange
                  .getResponse()
                  .getHeaders()
                  .set(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
              return problem(exchange, policy);
            });
  }

  private static Policy policy(String path) {
    if ("/sdk/v1/snapshot".equals(path)
        || (path.startsWith("/sdk/v1/client/") && path.endsWith("/snapshot"))) {
      return Policy.SNAPSHOT;
    }
    if ("/sdk/v1/stream".equals(path)
        || (path.startsWith("/sdk/v1/client/") && path.endsWith("/stream"))) {
      return Policy.STREAM;
    }
    if ("/events/v1/evaluations/batch".equals(path)
        || (path.startsWith("/events/v1/client/") && path.endsWith("/evaluations/batch"))) {
      return Policy.ANALYTICS;
    }
    return null;
  }

  private static UUID keyId(ServerWebExchange exchange) {
    SdkCredentialScope server = exchange.getAttribute(SdkAuthenticationWebFilter.SCOPE_ATTRIBUTE);
    if (server != null) {
      return server.keyId();
    }
    BrowserClientScope browser = exchange.getAttribute(BrowserClientWebFilter.SCOPE_ATTRIBUTE);
    return browser == null ? null : browser.keyId();
  }

  private static Mono<Void> problem(ServerWebExchange exchange, Policy policy) {
    HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
    String code = policy.name() + "_RATE_LIMITED";
    exchange.getResponse().setStatusCode(status);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    String body =
        "{\"type\":\"https://launchforge.dev/problems/"
            + code.toLowerCase(Locale.ROOT)
            + "\",\"title\":\"Too Many Requests\",\"status\":429,\"code\":\""
            + code
            + "\",\"correlationId\":\""
            + EdgeCorrelationWebFilter.get(exchange)
            + "\"}";
    DataBuffer buffer =
        exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }
}
