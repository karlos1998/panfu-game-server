package it.letscode.panfu.minigame;

import static it.letscode.panfu.support.TestSessions.authenticated;
import static org.assertj.core.api.Assertions.assertThat;

import it.letscode.panfu.session.PlayerSession;
import it.letscode.panfu.session.SessionRegistry;
import it.letscode.panfu.support.RecordingConnection;
import org.junit.jupiter.api.Test;

class MultiplayerServiceTest {

    @Test
    void pairsFourBoomPlayersRelaysSanitizedMessagesAndHandlesLeaving() {
        SessionRegistry registry = new SessionRegistry();
        RecordingConnection firstConnection = new RecordingConnection("first");
        RecordingConnection secondConnection = new RecordingConnection("second");
        PlayerSession first = authenticated(firstConnection, 1, "First");
        PlayerSession second = authenticated(secondConnection, 2, "Second");
        registry.register(first);
        registry.register(second);
        MultiplayerService multiplayer = new MultiplayerService(registry);

        multiplayer.enter(25, 0, first);
        multiplayer.enter(25, 0, second);
        multiplayer.relay(25, "pick;", "column|3", first);
        multiplayer.leave(first);

        assertThat(firstConnection.messages()).contains("15;25;0;setPlayer;2|");
        assertThat(secondConnection.messages())
                .contains("15;25;0;setPlayer;1|")
                .contains("15;25;1;1;pick;column3|")
                .contains("15;25;1;unsetPlayer;1|");
        assertThat(second.multiplayerPartnerId()).isNull();
    }

    @Test
    void onlyPairsRockPaperScissorsPlayersInTheSameAudience() {
        SessionRegistry registry = new SessionRegistry();
        RecordingConnection firstConnection = new RecordingConnection("first");
        RecordingConnection secondConnection = new RecordingConnection("second");
        PlayerSession first = authenticated(firstConnection, 1, "First");
        PlayerSession second = authenticated(secondConnection, 2, "Second");
        first.joinRoom(8, 0, 0);
        second.joinRoom(8, 0, 0);
        registry.register(first);
        registry.register(second);
        MultiplayerService multiplayer = new MultiplayerService(registry);

        multiplayer.enter(41, second.playerId(), first);
        multiplayer.enter(41, first.playerId(), second);

        assertThat(firstConnection.messages()).containsExactly("15;41;0;setPlayer;2|");
        assertThat(secondConnection.messages()).containsExactly("15;41;0;setPlayer;1|");
    }
}
