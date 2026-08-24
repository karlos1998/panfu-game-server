package it.letscode.panfu.command;

import it.letscode.panfu.petrace.PetRaceMatchmakingService;
import it.letscode.panfu.protocol.IncomingPacket;
import it.letscode.panfu.protocol.PacketHeaders;
import it.letscode.panfu.protocol.PacketReader;
import it.letscode.panfu.session.PlayerSession;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class PetRaceCommandHandler implements CommandHandler {

    private final PetRaceMatchmakingService matchmaking;

    public PetRaceCommandHandler(PetRaceMatchmakingService matchmaking) {
        this.matchmaking = matchmaking;
    }

    @Override
    public Set<Integer> headers() {
        return Set.of(
                PacketHeaders.PET_RACE_PRIVATE_INVITE,
                PacketHeaders.PET_RACE_PRIVATE_RESPONSE,
                PacketHeaders.PET_RACE_PRIVATE_CANCELLED,
                PacketHeaders.PET_RACE_PUBLIC_MATCHMAKING,
                PacketHeaders.PET_RACE_PUBLIC_BOT,
                PacketHeaders.PET_RACE_PUBLIC_CANCELLED);
    }

    @Override
    public void handle(IncomingPacket packet, PlayerSession session) {
        PacketReader reader = packet.reader();
        switch (packet.header()) {
            case PacketHeaders.PET_RACE_PRIVATE_INVITE -> matchmaking.invite(session, reader.readInt());
            case PacketHeaders.PET_RACE_PRIVATE_RESPONSE ->
                    matchmaking.respond(session, reader.readInt(), reader.readInt() == 1);
            case PacketHeaders.PET_RACE_PRIVATE_CANCELLED -> matchmaking.cancelPrivate(session, reader.readInt());
            case PacketHeaders.PET_RACE_PUBLIC_MATCHMAKING -> matchmaking.joinPublic(session);
            case PacketHeaders.PET_RACE_PUBLIC_BOT -> matchmaking.matchWithBot(session);
            case PacketHeaders.PET_RACE_PUBLIC_CANCELLED -> matchmaking.cancelPublic(session);
            default -> throw new IllegalArgumentException("Unsupported pet race command");
        }
    }
}
