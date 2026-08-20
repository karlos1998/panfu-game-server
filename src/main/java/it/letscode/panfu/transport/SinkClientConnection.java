package it.letscode.panfu.transport;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

public final class SinkClientConnection implements ClientConnection {

    private final String id;
    private final String remoteIp;
    private final Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
    private final AtomicBoolean closed = new AtomicBoolean();

    public SinkClientConnection(String id, String remoteIp) {
        this.id = Objects.requireNonNull(id);
        this.remoteIp = Objects.requireNonNull(remoteIp);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String remoteIp() {
        return remoteIp;
    }

    @Override
    public void send(String payload) {
        if (!closed.get()) {
            outbound.tryEmitNext(payload);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            outbound.tryEmitComplete();
        }
    }

    public Flux<String> outbound() {
        return outbound.asFlux();
    }
}
