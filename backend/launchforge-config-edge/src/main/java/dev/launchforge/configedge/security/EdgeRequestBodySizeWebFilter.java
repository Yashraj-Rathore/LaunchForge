package dev.launchforge.configedge.security;

import dev.launchforge.configedge.configuration.AnalyticsIngestionProperties;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public final class EdgeRequestBodySizeWebFilter implements WebFilter {
  private final int maximumAnalyticsBytes;

  public EdgeRequestBodySizeWebFilter(AnalyticsIngestionProperties properties) {
    maximumAnalyticsBytes = properties.maximumRequestBytes();
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String path = exchange.getRequest().getPath().value();
    long contentLength = exchange.getRequest().getHeaders().getContentLength();
    if (path.startsWith("/events/v1/") && contentLength > maximumAnalyticsBytes) {
      return tooLarge(exchange);
    }
    return chain.filter(exchange);
  }

  private static Mono<Void> tooLarge(ServerWebExchange exchange) {
    String code = "ANALYTICS_BODY_TOO_LARGE";
    exchange.getResponse().setStatusCode(HttpStatus.CONTENT_TOO_LARGE);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    String body =
        "{\"type\":\"https://launchforge.dev/problems/"
            + code.toLowerCase(Locale.ROOT)
            + "\",\"title\":\"Content Too Large\",\"status\":413,\"code\":\""
            + code
            + "\",\"correlationId\":\""
            + EdgeCorrelationWebFilter.get(exchange)
            + "\"}";
    DataBuffer buffer =
        exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }
}
