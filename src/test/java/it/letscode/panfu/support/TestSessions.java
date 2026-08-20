package it.letscode.panfu.support;

import it.letscode.panfu.config.GameServerProperties;
import it.letscode.panfu.protocol.PacketCodec;
import it.letscode.panfu.session.PlayerSession;
import java.time.Duration;
import java.util.List;

public final class TestSessions {

    private static final GameServerProperties PROPERTIES = new GameServerProperties(
            1,
            new GameServerProperties.Network("/game", 9595, true),
            new GameServerProperties.Security(List.of("http://localhost"), "secret", Duration.ofSeconds(30)),
            new GameServerProperties.Limits(8192, 64, 10, Duration.ofSeconds(30), Duration.ofMinutes(5)),
            new GameServerProperties.Rewards(Duration.ofSeconds(2), 100_000, 500));
    private static final PacketCodec CODEC = new PacketCodec(PROPERTIES);

    private TestSessions() {}

    public static PlayerSession authenticated(RecordingConnection connection, int id, String username) {
        PlayerSession session = new PlayerSession(connection, CODEC);
        session.authenticate(id, username, 0, 1);
        return session;
    }
}
