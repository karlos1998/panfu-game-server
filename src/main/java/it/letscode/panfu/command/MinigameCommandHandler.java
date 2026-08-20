package it.letscode.panfu.command;

import it.letscode.panfu.minigame.MultiplayerService;
import it.letscode.panfu.minigame.RewardPolicy;
import it.letscode.panfu.persistence.reward.RewardLedgerRepository;
import it.letscode.panfu.persistence.reward.RewardSettings;
import it.letscode.panfu.protocol.IncomingPacket;
import it.letscode.panfu.protocol.OutgoingPacket;
import it.letscode.panfu.protocol.PacketHeaders;
import it.letscode.panfu.protocol.PacketReader;
import it.letscode.panfu.session.PlayerSession;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class MinigameCommandHandler implements CommandHandler {

    private final MultiplayerService multiplayer;
    private final RewardPolicy rewards;
    private final RewardLedgerRepository ledger;

    public MinigameCommandHandler(
            MultiplayerService multiplayer,
            RewardPolicy rewards,
            RewardLedgerRepository ledger) {
        this.multiplayer = multiplayer;
        this.rewards = rewards;
        this.ledger = ledger;
    }

    @Override
    public Set<Integer> headers() {
        return Set.of(
                PacketHeaders.JOIN_GAME,
                PacketHeaders.ENTER_MULTIGAME,
                PacketHeaders.MULTIGAME,
                PacketHeaders.QUIT_GAME);
    }

    @Override
    public void handle(IncomingPacket packet, PlayerSession session) {
        PacketReader reader = packet.reader();
        switch (packet.header()) {
            case PacketHeaders.JOIN_GAME -> joinGame(reader.readInt(), session);
            case PacketHeaders.ENTER_MULTIGAME -> multiplayer.enter(reader.readInt(), reader.readInt(), session);
            case PacketHeaders.MULTIGAME -> relay(reader, session);
            case PacketHeaders.QUIT_GAME -> quitGame(reader, session);
            default -> throw new IllegalArgumentException("Unsupported minigame command");
        }
    }

    private void joinGame(int gameId, PlayerSession session) {
        if (gameId > 0 && gameId <= 10_000) {
            session.startGame(gameId);
        }
    }

    private void relay(PacketReader reader, PlayerSession session) {
        int gameId = reader.readInt();
        reader.readInt(); // Claimed sender ID is ignored.
        multiplayer.relay(gameId, reader.readString(), reader.readString(), session);
    }

    private void quitGame(PacketReader reader, PlayerSession session) {
        int gameId = reader.readInt();
        int score = reader.readInt();
        if (session.currentGame() == gameId
                && session.currentRound() != null
                && rewards.serverAwardsEnabled()) {
            UUID roundId = session.currentRound();
            RewardSettings settings = ledger.settingsFor(gameId);
            int coins = rewards.calculate(score, session.roundStartedAt(), Instant.now(), settings);
            int awarded = ledger.awardOnce(roundId, session.playerId(), gameId, score, coins);
            if (awarded > 0) {
                session.send(OutgoingPacket.header(PacketHeaders.PLAYER_INFO_UPDATED).writeInt(awarded));
            }
        }
        multiplayer.leave(session);
        session.finishGame();
        session.send(OutgoingPacket.header(PacketHeaders.ROOM_JOINED).writeInt(session.roomId()));
    }
}
