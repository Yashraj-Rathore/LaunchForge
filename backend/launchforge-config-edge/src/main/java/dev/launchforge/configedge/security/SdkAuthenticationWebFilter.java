package dev.launchforge.configedge.security;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
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

  public SdkAuthenticationWebFilter(SdkAuthenticationService authenticationService) {
    this.authenticationService = authenticationService;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (!exchange.getRequest().getPath().value().startsWith("/sdk/v1/")) {
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
            exception ->
                problem(
                    exchange,
                    exception.isForbidden() ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED,
                    exception.isForbidden() ? "SDK_SCOPE_INACTIVE" : "SDK_KEY_INVALID"))
        .onErrorResume(
            DataAccessException.class,
            exception ->
                problem(exchange, HttpStatus.SERVICE_UNAVAILABLE, "EDGE_DATABASE_UNAVAILABLE"));
  }

  private static String oneAuthorizationValue(HttpHeaders headers) {
    List<String> values = headers.get(HttpHeaders.AUTHORIZATION);
    return values == null || values.size() != 1 ? null : values.getFirst();
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
            + UUID.randomUUID()
            + "\"}";
    DataBuffer buffer =
        exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }
}
