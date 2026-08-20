package it.letscode.panfu.minigame;

import it.letscode.panfu.protocol.OutgoingPacket;
import it.letscode.panfu.protocol.PacketHeaders;
import it.letscode.panfu.session.PlayerSession;
import it.letscode.panfu.session.SessionRegistry;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.stereotype.Service;

@Service
public final class MultiplayerService {

    private static final int FOUR_BOOM = 25;
    private static final int ROCK_PAPER_SCISSORS = 41;
    private final ConcurrentLinkedQueue<Integer> fourBoomQueue = new ConcurrentLinkedQueue<>();
    private final SessionRegistry sessions;

    public MultiplayerService(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    public void enter(int gameId, int requestedPartnerId, PlayerSession player) {
        if (gameId == FOUR_BOOM) {
            enterFourBoom(player);
        } else if (gameId == ROCK_PAPER_SCISSORS) {
            enterRockPaperScissors(requestedPartnerId, player);
        }
    }

    public void relay(int claimedGameId, String action, String parameter, PlayerSession sender) {
        if (claimedGameId != sender.currentGame() || sender.multiplayerPartnerId() == null) {
            return;
        }
        sessions.find(sender.multiplayerPartnerId()).ifPresent(partner -> partner.send(
                OutgoingPacket.header(PacketHeaders.MULTIGAME_MESSAGE)
                        .writeInt(sender.currentGame())
                        .writeInt(sender.playerId())
                        .writeInt(sender.playerId())
                        .writeString(limited(action, 80))
                        .writeString(limited(parameter, 200))));
    }

    public void leave(PlayerSession player) {
        fourBoomQueue.remove(player.playerId());
        Integer partnerId = player.multiplayerPartnerId();
        if (partnerId == null) {
            return;
        }
        sessions.find(partnerId).ifPresent(partner -> {
            partner.send(OutgoingPacket.header(PacketHeaders.MULTIGAME_MESSAGE)
                    .writeInt(player.currentGame())
                    .writeInt(player.playerId())
                    .writeString("unsetPlayer")
                    .writeInt(player.playerId()));
            partner.multiplayerPartnerId(null);
        });
        player.multiplayerPartnerId(null);
    }

    private void enterFourBoom(PlayerSession player) {
        player.startGame(FOUR_BOOM);
        Integer waitingId;
        while ((waitingId = fourBoomQueue.poll()) != null) {
            PlayerSession opponent = sessions.find(waitingId).orElse(null);
            if (opponent != null && opponent.playerId() != player.playerId()
                    && opponent.currentGame() == FOUR_BOOM && opponent.multiplayerPartnerId() == null) {
                pair(player, opponent, FOUR_BOOM);
                return;
            }
        }
        fourBoomQueue.offer(player.playerId());
    }

    private void enterRockPaperScissors(int requestedPartnerId, PlayerSession player) {
        PlayerSession partner = sessions.find(requestedPartnerId).orElse(null);
        if (partner == null || partner.playerId() == player.playerId()
                || partner.roomId() != player.roomId() || partner.home() != player.home()) {
            return;
        }
        player.startGame(ROCK_PAPER_SCISSORS);
        player.multiplayerPartnerId(partner.playerId());
        if (partner.currentGame() == ROCK_PAPER_SCISSORS
                && partner.multiplayerPartnerId() != null
                && partner.multiplayerPartnerId() == player.playerId()) {
            pair(player, partner, ROCK_PAPER_SCISSORS);
        }
    }

    private void pair(PlayerSession first, PlayerSession second, int gameId) {
        first.multiplayerPartnerId(second.playerId());
        second.multiplayerPartnerId(first.playerId());
        first.send(startPacket(gameId, 1));
        second.send(startPacket(gameId, 2));
    }

    private OutgoingPacket startPacket(int gameId, int playerNumber) {
        return OutgoingPacket.header(PacketHeaders.MULTIGAME_MESSAGE)
                .writeInt(gameId)
                .writeInt(0)
                .writeString("setPlayer")
                .writeInt(playerNumber);
    }

    private String limited(String value, int limit) {
        String clean = value == null ? "" : value.replace(";", "").replace("|", "");
        return clean.substring(0, Math.min(clean.length(), limit));
    }
}
