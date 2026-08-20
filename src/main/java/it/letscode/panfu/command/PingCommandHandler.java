package it.letscode.panfu.command;

import it.letscode.panfu.protocol.IncomingPacket;
import it.letscode.panfu.protocol.PacketHeaders;
import it.letscode.panfu.session.PlayerSession;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class PingCommandHandler implements CommandHandler {

    @Override
    public Set<Integer> headers() {
        return Set.of(PacketHeaders.PING);
    }

    @Override
    public void handle(IncomingPacket packet, PlayerSession session) {
        // The legacy client expects the connection itself to act as acknowledgement.
    }
}
