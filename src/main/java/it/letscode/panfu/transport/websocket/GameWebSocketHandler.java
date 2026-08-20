package it.letscode.panfu.transport.websocket;

import it.letscode.panfu.config.GameServerProperties;
import it.letscode.panfu.session.PlayerSession;
import it.letscode.panfu.transport.ConnectionLimiter;
import it.letscode.panfu.transport.FrameAccumulator;
import it.letscode.panfu.transport.SinkClientConnection;
import it.letscode.panfu.transport.TransportPipeline;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public final class GameWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);
    private final TransportPipeline pipeline;
    private final ConnectionLimiter limiter;
    private final Duration idleTimeout;

    public GameWebSocketHandler(
            TransportPipeline pipeline,
            ConnectionLimiter limiter,
            GameServerProperties properties) {
        this.pipeline = pipeline;
        this.limiter = limiter;
        this.idleTimeout = properties.limits().idleTimeout();
    }

    @Override
    public Mono<Void> handle(WebSocketSession webSocket) {
        String ip = remoteIp(webSocket);
        if (!limiter.acquire(ip)) {
            return webSocket.close();
        }

        SinkClientConnection connection = new SinkClientConnection(webSocket.getId(), ip);
        PlayerSession player = pipeline.session(connection);
        FrameAccumulator frames = pipeline.accumulator();
        Mono<Void> send = webSocket.send(connection.outbound().map(payload ->
                webSocket.binaryMessage(factory -> factory.wrap(payload.getBytes(StandardCharsets.UTF_8)))));
        Mono<Void> receive = webSocket.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .concatMap(chunk -> pipeline.accept(chunk, frames, player))
                .timeout(idleTimeout)
                .then()
                .doFinally(signal -> connection.close());

        log.info("WebSocket connected connectionId={} remoteIp={}", connection.id(), ip);
        return Mono.when(send, receive)
                .onErrorResume(error -> {
                    log.warn("WebSocket closed after protocol error connectionId={}", connection.id(), error);
                    return Mono.empty();
                })
                .doFinally(signal -> {
                    connection.close();
                    pipeline.disconnected(player);
                    limiter.release(ip);
                });
    }

    private String remoteIp(WebSocketSession session) {
        InetSocketAddress address = session.getHandshakeInfo().getRemoteAddress();
        return address == null || address.getAddress() == null
                ? "unknown"
                : address.getAddress().getHostAddress();
    }
}
