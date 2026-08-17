package dev.launchforge.configedge.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class EdgePrivacySafeRequestLoggingWebFilter implements WebFilter {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(EdgePrivacySafeRequestLoggingWebFilter.class);

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String method = exchange.getRequest().getMethod().name();
    String family = routeFamily(exchange.getRequest().getPath().value());
    return chain
        .filter(exchange)
        .doFinally(
            ignored ->
                LOGGER.info(
                    "EdgeRequestCompleted method={} routeFamily={} status={} correlationId={}",
                    method,
                    family,
                    exchange.getResponse().getStatusCode() == null
                        ? 200
                        : exchange.getResponse().getStatusCode().value(),
                    EdgeCorrelationWebFilter.get(exchange)));
  }

  private static String routeFamily(String path) {
    if ("/sdk/v1/snapshot".equals(path)) {
      return "server_snapshot";
    }
    if ("/sdk/v1/stream".equals(path)) {
      return "server_stream";
    }
    if (path.startsWith("/sdk/v1/client/") && path.endsWith("/snapshot")) {
      return "browser_snapshot";
    }
    if (path.startsWith("/sdk/v1/client/") && path.endsWith("/stream")) {
      return "browser_stream";
    }
    if (path.startsWith("/events/v1/")) {
      return "analytics";
    }
    if (path.startsWith("/actuator/")) {
      return "actuator";
    }
    return "other";
  }
}
