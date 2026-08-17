package dev.launchforge.configedge.security;

import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class EdgeCorrelationWebFilter implements WebFilter {
  public static final String HEADER_NAME = "X-Correlation-ID";
  private static final String ATTRIBUTE_NAME = EdgeCorrelationWebFilter.class.getName();
  private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String correlationId = normalize(exchange.getRequest().getHeaders().getFirst(HEADER_NAME));
    exchange.getAttributes().put(ATTRIBUTE_NAME, correlationId);
    exchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);
    return chain.filter(exchange);
  }

  public static String get(ServerWebExchange exchange) {
    Object value = exchange.getAttribute(ATTRIBUTE_NAME);
    return value instanceof String correlationId ? correlationId : UUID.randomUUID().toString();
  }

  private static String normalize(String candidate) {
    return candidate != null && SAFE_VALUE.matcher(candidate).matches()
        ? candidate
        : UUID.randomUUID().toString();
  }
}
