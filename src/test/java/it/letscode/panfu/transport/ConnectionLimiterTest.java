package it.letscode.panfu.transport;

import static org.assertj.core.api.Assertions.assertThat;

import it.letscode.panfu.config.GameServerProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectionLimiterTest {

    @Test
    void enforcesAndReleasesPerIpLimit() {
        ConnectionLimiter limiter = new ConnectionLimiter(new GameServerProperties(
                1,
                new GameServerProperties.Network("/game", 9595, true),
                new GameServerProperties.Security(List.of("http://localhost"), "secret", Duration.ofSeconds(30)),
                new GameServerProperties.Limits(100, 10, 2, Duration.ofSeconds(30), Duration.ofMinutes(5)),
                new GameServerProperties.Rewards(true, Duration.ofSeconds(2), 100, 10)));

        assertThat(limiter.acquire("127.0.0.1")).isTrue();
        assertThat(limiter.acquire("127.0.0.1")).isTrue();
        assertThat(limiter.acquire("127.0.0.1")).isFalse();
        assertThat(limiter.connectionsFor("127.0.0.1")).isEqualTo(2);
        limiter.release("127.0.0.1");
        assertThat(limiter.acquire("127.0.0.1")).isTrue();
    }
}
