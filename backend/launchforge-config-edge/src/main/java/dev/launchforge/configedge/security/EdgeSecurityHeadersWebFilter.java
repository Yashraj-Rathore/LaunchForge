package dev.launchforge.configedge.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class EdgeSecurityHeadersWebFilter implements WebFilter {
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    HttpHeaders headers = exchange.getResponse().getHeaders();
    headers.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
    headers.set("X-Content-Type-Options", "nosniff");
    headers.set("Referrer-Policy", "no-referrer");
    headers.set("X-Frame-Options", "DENY");
    if ("https".equalsIgnoreCase(exchange.getRequest().getURI().getScheme())) {
      headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
    }
    return chain.filter(exchange);
  }
}
