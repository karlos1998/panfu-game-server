package it.letscode.panfu.command;

import static it.letscode.panfu.support.TestSessions.authenticated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.letscode.panfu.minigame.MultiplayerService;
import it.letscode.panfu.minigame.RewardPolicy;
import it.letscode.panfu.persistence.reward.RewardLedgerRepository;
import it.letscode.panfu.persistence.reward.RewardSettings;
import it.letscode.panfu.protocol.IncomingPacket;
import it.letscode.panfu.protocol.PacketHeaders;
import it.letscode.panfu.session.PlayerSession;
import it.letscode.panfu.session.SessionRegistry;
import it.letscode.panfu.support.RecordingConnection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MinigameCommandHandlerTest {

    @Test
    void awardsAValidatedRoundExactlyThroughTheLedger() {
        SessionRegistry registry = new SessionRegistry();
        MultiplayerService multiplayer = new MultiplayerService(registry);
        RewardPolicy policy = mock(RewardPolicy.class);
        RewardLedgerRepository ledger = mock(RewardLedgerRepository.class);
        RewardSettings settings = new RewardSettings(true, BigDecimal.ONE, 100);
        when(policy.serverAwardsEnabled()).thenReturn(true);
        when(ledger.settingsFor(7)).thenReturn(settings);
        when(policy.calculate(org.mockito.ArgumentMatchers.eq(200), any(Instant.class), any(Instant.class),
                org.mockito.ArgumentMatchers.eq(settings))).thenReturn(42);
        when(ledger.awardOnce(any(UUID.class), org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.eq(7), org.mockito.ArgumentMatchers.eq(200),
                org.mockito.ArgumentMatchers.eq(42))).thenReturn(42);
        RecordingConnection connection = new RecordingConnection("player");
        PlayerSession player = authenticated(connection, 5, "Player");
        player.joinRoom(12, 0, 0);
        UUID roundId = player.startGame(7);

        new MinigameCommandHandler(multiplayer, policy, ledger).handle(
                new IncomingPacket(PacketHeaders.QUIT_GAME, List.of("7", "200")), player);

        verify(ledger).awardOnce(roundId, 5, 7, 200, 42);
        assertThat(connection.messages()).containsExactly("35;42|", "10;12|");
        assertThat(player.currentRound()).isNull();
    }

    @Test
    void doesNotDoubleAwardLegacyClientRewardsWhenServerAwardsAreDisabled() {
        SessionRegistry registry = new SessionRegistry();
        MultiplayerService multiplayer = new MultiplayerService(registry);
        RewardPolicy policy = mock(RewardPolicy.class);
        RewardLedgerRepository ledger = mock(RewardLedgerRepository.class);
        RecordingConnection connection = new RecordingConnection("player");
        PlayerSession player = authenticated(connection, 5, "Player");
        player.joinRoom(12, 0, 0);
        player.startGame(7);

        new MinigameCommandHandler(multiplayer, policy, ledger).handle(
                new IncomingPacket(PacketHeaders.QUIT_GAME, List.of("7", "200")), player);

        verify(policy).serverAwardsEnabled();
        org.mockito.Mockito.verifyNoInteractions(ledger);
        assertThat(connection.messages()).containsExactly("10;12|");
        assertThat(player.currentRound()).isNull();
    }

    @Test
    void ignoresARewardClaimForADifferentGame() {
        MultiplayerService multiplayer = new MultiplayerService(new SessionRegistry());
        RewardPolicy policy = mock(RewardPolicy.class);
        RewardLedgerRepository ledger = mock(RewardLedgerRepository.class);
        RecordingConnection connection = new RecordingConnection("player");
        PlayerSession player = authenticated(connection, 5, "Player");
        player.joinRoom(12, 0, 0);
        player.startGame(7);

        new MinigameCommandHandler(multiplayer, policy, ledger).handle(
                new IncomingPacket(PacketHeaders.QUIT_GAME, List.of("8", "999999")), player);

        assertThat(connection.messages()).containsExactly("10;12|");
        org.mockito.Mockito.verifyNoInteractions(policy, ledger);
    }
}
