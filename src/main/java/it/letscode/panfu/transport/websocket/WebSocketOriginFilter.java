package it.letscode.panfu.transport.websocket;

import it.letscode.panfu.config.GameServerProperties;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class WebSocketOriginFilter implements WebFilter {

    private final String websocketPath;
    private final Set<String> allowedOrigins;

    public WebSocketOriginFilter(GameServerProperties properties) {
        this.websocketPath = properties.network().websocketPath();
        this.allowedOrigins = Set.copyOf(properties.security().allowedOrigins());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!exchange.getRequest().getPath().value().equals(websocketPath)
                || !"websocket".equalsIgnoreCase(exchange.getRequest().getHeaders().getUpgrade())) {
            return chain.filter(exchange);
        }
        String origin = exchange.getRequest().getHeaders().getOrigin();
        if (origin == null || !allowedOrigins.contains(origin)) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }
}
