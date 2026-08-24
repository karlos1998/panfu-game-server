package it.letscode.panfu.petrace;

import it.letscode.panfu.protocol.OutgoingPacket;
import it.letscode.panfu.protocol.PacketHeaders;
import it.letscode.panfu.session.PlayerSession;
import it.letscode.panfu.session.SessionRegistry;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public final class PetRaceMatchmakingService {

    private final SessionRegistry sessions;
    private final ConcurrentHashMap<Integer, Integer> invitations = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Integer> publicQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Integer, PetRaceMatch> matches = new ConcurrentHashMap<>();

    public PetRaceMatchmakingService(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    public void invite(PlayerSession challenger, int targetId) {
        if (targetId <= 0 || targetId == challenger.playerId()) {
            return;
        }
        sessions.find(targetId).ifPresent(target -> {
            invitations.put(targetId, challenger.playerId());
            target.send(OutgoingPacket.header(PacketHeaders.PET_RACE_PRIVATE_REQUEST)
                    .writeInt(challenger.playerId())
                    .writeString(challenger.username()));
        });
    }

    public void respond(PlayerSession responder, int challengerId, boolean accepted) {
        if (!invitations.remove(responder.playerId(), challengerId)) {
            return;
        }
        PlayerSession challenger = sessions.find(challengerId).orElse(null);
        if (challenger == null) {
            return;
        }
        if (!accepted) {
            challenger.send(OutgoingPacket.header(PacketHeaders.PET_RACE_RESPONSE)
                    .writeInt(responder.playerId()).writeInt(0));
            return;
        }
        PetRaceMatch match = createMatch(Set.of(challenger.playerId(), responder.playerId()));
        OutgoingPacket response = OutgoingPacket.header(PacketHeaders.PET_RACE_RESPONSE).writeInt(match.ticket());
        challenger.send(response);
        responder.send(response);
    }

    public void cancelPrivate(PlayerSession challenger, int targetId) {
        if (invitations.remove(targetId, challenger.playerId())) {
            sessions.find(targetId).ifPresent(target ->
                    target.send(OutgoingPacket.header(PacketHeaders.PET_RACE_INVITE_CANCELLED)));
        }
    }

    public void joinPublic(PlayerSession player) {
        removeFromPublicQueue(player.playerId());
        Integer opponentId;
        while ((opponentId = publicQueue.poll()) != null) {
            if (opponentId == player.playerId()) {
                continue;
            }
            PlayerSession opponent = sessions.find(opponentId).orElse(null);
            if (opponent == null) {
                continue;
            }
            PetRaceMatch match = createMatch(Set.of(player.playerId(), opponent.playerId()));
            player.send(OutgoingPacket.header(PacketHeaders.PET_RACE_PUBLIC_MATCH_FOUND)
                    .writeInt(match.ticket()).writeInt(opponent.playerId()));
            opponent.send(OutgoingPacket.header(PacketHeaders.PET_RACE_PUBLIC_MATCH_FOUND)
                    .writeInt(match.ticket()).writeInt(player.playerId()));
            return;
        }
        publicQueue.offer(player.playerId());
    }

    public void matchWithBot(PlayerSession player) {
        removeFromPublicQueue(player.playerId());
        PetRaceMatch match = createMatch(Set.of(player.playerId(), 0));
        player.send(OutgoingPacket.header(PacketHeaders.PET_RACE_PUBLIC_MATCH_FOUND)
                .writeInt(match.ticket()).writeInt(0));
    }

    public void cancelPublic(PlayerSession player) {
        removeFromPublicQueue(player.playerId());
    }

    public Optional<PetRaceMatch> findMatch(int ticket) {
        return Optional.ofNullable(matches.get(ticket));
    }

    public void finish(int ticket) {
        matches.remove(ticket);
    }

    private PetRaceMatch createMatch(Set<Integer> players) {
        int ticket;
        do {
            ticket = ThreadLocalRandom.current().nextInt(100_000_000, Integer.MAX_VALUE);
        } while (matches.containsKey(ticket));
        PetRaceMatch match = new PetRaceMatch(ticket, players);
        matches.put(ticket, match);
        return match;
    }

    private void removeFromPublicQueue(int playerId) {
        for (Iterator<Integer> iterator = publicQueue.iterator(); iterator.hasNext();) {
            if (iterator.next() == playerId) {
                iterator.remove();
            }
        }
    }
}
