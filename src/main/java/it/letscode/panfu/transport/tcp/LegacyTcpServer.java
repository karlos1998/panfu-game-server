package it.letscode.panfu.transport.tcp;

import it.letscode.panfu.config.GameServerProperties;
import it.letscode.panfu.session.PlayerSession;
import it.letscode.panfu.transport.ConnectionLimiter;
import it.letscode.panfu.transport.FrameAccumulator;
import it.letscode.panfu.transport.SinkClientConnection;
import it.letscode.panfu.transport.TransportPipeline;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.tcp.TcpServer;

@Component
public final class LegacyTcpServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(LegacyTcpServer.class);
    private final TransportPipeline pipeline;
    private final ConnectionLimiter limiter;
    private final GameServerProperties.Network network;
    private final Duration idleTimeout;
    private volatile DisposableServer server;

    public LegacyTcpServer(
            TransportPipeline pipeline,
            ConnectionLimiter limiter,
            GameServerProperties properties) {
        this.pipeline = pipeline;
        this.limiter = limiter;
        this.network = properties.network();
        this.idleTimeout = properties.limits().idleTimeout();
    }

    @Override
    public void start() {
        if (!network.legacyTcpEnabled() || isRunning()) {
            return;
        }
        server = TcpServer.create()
                .host("0.0.0.0")
                .port(network.legacyTcpPort())
                .handle((inbound, outbound) -> {
                    AtomicReference<InetSocketAddress> remoteAddress = new AtomicReference<>();
                    inbound.withConnection(connection ->
                            remoteAddress.set((InetSocketAddress) connection.channel().remoteAddress()));
                    String ip = remoteIp(remoteAddress.get());
                    if (!limiter.acquire(ip)) {
                        return Mono.empty();
                    }
                    SinkClientConnection connection = new SinkClientConnection(UUID.randomUUID().toString(), ip);
                    PlayerSession player = pipeline.session(connection);
                    FrameAccumulator frames = pipeline.accumulator();
                    Mono<Void> send = outbound.sendString(connection.outbound(), StandardCharsets.UTF_8).then();
                    Mono<Void> receive = inbound.receive()
                            .asString(StandardCharsets.UTF_8)
                            .concatMap(chunk -> pipeline.accept(chunk, frames, player))
                            .timeout(idleTimeout)
                            .then()
                            .doFinally(signal -> connection.close());
                    return Mono.when(send, receive)
                            .onErrorResume(error -> {
                                log.warn("Legacy TCP protocol error connectionId={}", connection.id(), error);
                                return Mono.empty();
                            })
                            .doFinally(signal -> {
                                connection.close();
                                pipeline.disconnected(player);
                                limiter.release(ip);
                            });
                })
                .bindNow();
        log.info("Legacy TCP server listening on port={}", network.legacyTcpPort());
    }

    @Override
    public void stop() {
        DisposableServer current = server;
        if (current != null) {
            current.disposeNow(Duration.ofSeconds(10));
            server = null;
        }
    }

    @Override
    public boolean isRunning() {
        DisposableServer current = server;
        return current != null && !current.isDisposed();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    public int boundPort() {
        DisposableServer current = server;
        if (current == null) {
            throw new IllegalStateException("Legacy TCP server is not running");
        }
        return current.port();
    }

    private String remoteIp(InetSocketAddress address) {
        return address == null || address.getAddress() == null
                ? "unknown"
                : address.getAddress().getHostAddress();
    }
}
