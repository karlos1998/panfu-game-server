package it.letscode.panfu.command;

import it.letscode.panfu.protocol.IncomingPacket;
import it.letscode.panfu.session.PlayerSession;
import java.util.Set;

public interface CommandHandler {

    Set<Integer> headers();

    default boolean requiresAuthentication() {
        return true;
    }

    void handle(IncomingPacket packet, PlayerSession session);
}
