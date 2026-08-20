package it.letscode.panfu.transport;

import it.letscode.panfu.command.CommandDispatcher;
import it.letscode.panfu.config.GameServerProperties;
import it.letscode.panfu.protocol.IncomingPacket;
import it.letscode.panfu.protocol.PacketCodec;
import it.letscode.panfu.session.PlayerSession;
import it.letscode.panfu.session.SessionLifecycleService;
import java.util.List;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public final class TransportPipeline {

    private final PacketCodec codec;
    private final CommandDispatcher dispatcher;
    private final SessionLifecycleService lifecycle;
    private final int maxFrameBytes;

    public TransportPipeline(
            PacketCodec codec,
            CommandDispatcher dispatcher,
            SessionLifecycleService lifecycle,
            GameServerProperties properties) {
        this.codec = codec;
        this.dispatcher = dispatcher;
        this.lifecycle = lifecycle;
        this.maxFrameBytes = properties.limits().maxFrameBytes();
    }

    public FrameAccumulator accumulator() {
        return new FrameAccumulator(maxFrameBytes);
    }

    public PlayerSession session(ClientConnection connection) {
        return new PlayerSession(connection, codec);
    }

    public Flux<Void> accept(String chunk, FrameAccumulator accumulator, PlayerSession session) {
        List<String> frames = accumulator.append(chunk);
        return Flux.fromIterable(frames)
                .concatMap(frame -> Flux.fromIterable(codec.decodeCompleteFrames(frame)))
                .concatMap(packet -> dispatcher.dispatch(packet, session));
    }

    public void disconnected(PlayerSession session) {
        lifecycle.disconnect(session);
    }
}
