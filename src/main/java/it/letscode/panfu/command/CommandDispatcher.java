package it.letscode.panfu.command;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import it.letscode.panfu.protocol.IncomingPacket;
import it.letscode.panfu.session.PlayerSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public final class CommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);
    private final Map<Integer, CommandHandler> handlers;
    private final Counter rejectedCommands;

    public CommandDispatcher(List<CommandHandler> commandHandlers, MeterRegistry meterRegistry) {
        Map<Integer, CommandHandler> mapped = new HashMap<>();
        for (CommandHandler handler : commandHandlers) {
            for (int header : handler.headers()) {
                if (mapped.putIfAbsent(header, handler) != null) {
                    throw new IllegalStateException("Duplicate command handler for header " + header);
                }
            }
        }
        this.handlers = Map.copyOf(mapped);
        this.rejectedCommands = meterRegistry.counter("panfu.commands.rejected");
    }

    public Mono<Void> dispatch(IncomingPacket packet, PlayerSession session) {
        return Mono.fromRunnable(() -> dispatchSynchronously(packet, session))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    void dispatchSynchronously(IncomingPacket packet, PlayerSession session) {
        CommandHandler handler = handlers.get(packet.header());
        if (handler == null) {
            rejectedCommands.increment();
            log.debug("Ignoring unknown command header={}", packet.header());
            return;
        }
        if (handler.requiresAuthentication() && !session.authenticated()) {
            rejectedCommands.increment();
            log.warn("Rejected command before authentication connectionId={} header={}",
                    session.connection().id(), packet.header());
            return;
        }
        try {
            handler.handle(packet, session);
        } catch (RuntimeException exception) {
            rejectedCommands.increment();
            log.warn("Rejected malformed command connectionId={} header={}",
                    session.connection().id(), packet.header(), exception);
            session.disconnect("KICK_LOGIN_FAILED_MSG");
        }
    }
}
