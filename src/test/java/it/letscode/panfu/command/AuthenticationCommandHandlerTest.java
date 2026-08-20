package it.letscode.panfu.command;

import static org.assertj.core.api.Assertions.assertThat;

import it.letscode.panfu.config.GameServerProperties;
import it.letscode.panfu.persistence.player.PlayerAccount;
import it.letscode.panfu.persistence.player.PlayerAccountRepository;
import it.letscode.panfu.persistence.server.GameServerStatusRepository;
import it.letscode.panfu.protocol.IncomingPacket;
import it.letscode.panfu.protocol.PacketCodec;
import it.letscode.panfu.protocol.PacketHeaders;
import it.letscode.panfu.session.AudienceService;
import it.letscode.panfu.session.PlayerSession;
import it.letscode.panfu.session.SessionLifecycleService;
import it.letscode.panfu.session.SessionRegistry;
import it.letscode.panfu.transport.ClientConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuthenticationCommandHandlerTest {

    private final GameServerProperties properties = new GameServerProperties(
            3,
            new GameServerProperties.Network("/game", 9595, true),
            new GameServerProperties.Security(List.of("http://localhost"), "secret", Duration.ofSeconds(30)),
            new GameServerProperties.Limits(1000, 16, 10, Duration.ofSeconds(30), Duration.ofMinutes(5)),
            new GameServerProperties.Rewards(true, Duration.ofSeconds(2), 1000, 100));
    private final PacketCodec codec = new PacketCodec(properties);

    @Test
    void consumesTicketRegistersSessionAndPreservesLoginWireContract() {
        FakePlayers players = new FakePlayers(Optional.of(new PlayerAccount(7, "Panda", 1, 0)));
        SessionRegistry registry = new SessionRegistry();
        SessionLifecycleService lifecycle = new SessionLifecycleService(
                registry, new AudienceService(registry), players, (id, count) -> {}, properties);
        AuthenticationCommandHandler handler = new AuthenticationCommandHandler(players, lifecycle);
        RecordingConnection connection = new RecordingConnection();
        PlayerSession session = new PlayerSession(connection, codec);

        handler.handle(new IncomingPacket(PacketHeaders.LOGIN, List.of("7", "123456789", "22")), session);

        assertThat(session.authenticated()).isTrue();
        assertThat(session.roomId()).isEqualTo(22);
        assertThat(registry.find(7)).contains(session);
        assertThat(connection.messages).containsExactly("0;OK|", "10;22|");
        assertThat(players.onlineServer).isEqualTo(3);
    }

    @Test
    void rejectsInvalidOrReplayedTicket() {
        FakePlayers players = new FakePlayers(Optional.empty());
        SessionRegistry registry = new SessionRegistry();
        SessionLifecycleService lifecycle = new SessionLifecycleService(
                registry, new AudienceService(registry), players, (id, count) -> {}, properties);
        AuthenticationCommandHandler handler = new AuthenticationCommandHandler(players, lifecycle);
        RecordingConnection connection = new RecordingConnection();
        PlayerSession session = new PlayerSession(connection, codec);

        handler.handle(new IncomingPacket(PacketHeaders.LOGIN, List.of("7", "123", "1")), session);

        assertThat(session.authenticated()).isFalse();
        assertThat(connection.messages).containsExactly("0;FAILED|10;0|", "2;KICK_LOGIN_FAILED_MSG|");
        assertThat(connection.closed).isTrue();
    }

    private static final class FakePlayers implements PlayerAccountRepository {
        private final Optional<PlayerAccount> result;
        private int onlineServer = -1;
        private FakePlayers(Optional<PlayerAccount> result) { this.result = result; }
        public Optional<PlayerAccount> consumeLegacyTicket(int playerId, int ticket) { return result; }
        public void markOnline(int playerId, int gameServerId) { onlineServer = gameServerId; }
        public void markOffline(int playerId, int gameServerId) {}
    }

    private static final class RecordingConnection implements ClientConnection {
        private final List<String> messages = new ArrayList<>();
        private boolean closed;
        public String id() { return "connection"; }
        public String remoteIp() { return "127.0.0.1"; }
        public void send(String payload) { messages.add(payload); }
        public void close() { closed = true; }
    }
}
