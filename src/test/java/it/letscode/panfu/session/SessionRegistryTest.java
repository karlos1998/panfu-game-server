package it.letscode.panfu.session;

import static org.assertj.core.api.Assertions.assertThat;

import it.letscode.panfu.config.GameServerProperties;
import it.letscode.panfu.protocol.PacketCodec;
import it.letscode.panfu.transport.ClientConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionRegistryTest {

    private final PacketCodec codec = new PacketCodec(new GameServerProperties(
            1,
            new GameServerProperties.Network("/game", 9595, true),
            new GameServerProperties.Security(List.of("http://localhost"), "secret", Duration.ofSeconds(30)),
            new GameServerProperties.Limits(1000, 16, 10, Duration.ofSeconds(30), Duration.ofMinutes(5)),
            new GameServerProperties.Rewards(true, Duration.ofSeconds(2), 1000, 100)));

    @Test
    void rejectsDuplicateLoginWithoutReplacingExistingSession() {
        SessionRegistry registry = new SessionRegistry();
        PlayerSession existing = authenticated(7, "First");
        PlayerSession duplicate = authenticated(7, "Second");

        assertThat(registry.register(existing)).isTrue();
        assertThat(registry.register(duplicate)).isFalse();
        assertThat(registry.find(7)).contains(existing);
    }

    @Test
    void filtersPublicHomeAndSubroomSessions() {
        SessionRegistry registry = new SessionRegistry();
        PlayerSession publicRoom = authenticated(1, "Public");
        publicRoom.joinRoom(10, 1, 1);
        PlayerSession homeMain = authenticated(2, "Home");
        homeMain.joinHome(10, 1, 1);
        PlayerSession homeSubroom = authenticated(3, "Subroom");
        homeSubroom.joinHome(10, 1, 1);
        homeSubroom.subRoom(99);
        registry.register(publicRoom);
        registry.register(homeMain);
        registry.register(homeSubroom);

        assertThat(registry.inRoom(10, false, 0)).containsExactly(publicRoom);
        assertThat(registry.inRoom(10, true, 0)).containsExactly(homeMain);
        assertThat(registry.inRoom(10, true, 99)).containsExactly(homeSubroom);
    }

    private PlayerSession authenticated(int id, String username) {
        PlayerSession session = new PlayerSession(new RecordingConnection(), codec);
        session.authenticate(id, username, 0, 1);
        return session;
    }

    private static final class RecordingConnection implements ClientConnection {
        private final List<String> messages = new ArrayList<>();
        public String id() { return "test"; }
        public String remoteIp() { return "127.0.0.1"; }
        public void send(String payload) { messages.add(payload); }
        public void close() {}
    }
}
