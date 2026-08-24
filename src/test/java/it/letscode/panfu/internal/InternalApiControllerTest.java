package it.letscode.panfu.internal;

import static it.letscode.panfu.support.TestSessions.authenticated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import it.letscode.panfu.config.GameServerProperties;
import it.letscode.panfu.security.InternalRequestVerifier;
import it.letscode.panfu.session.PlayerSession;
import it.letscode.panfu.session.SessionRegistry;
import it.letscode.panfu.support.RecordingConnection;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class InternalApiControllerTest {

    private final InternalRequestVerifier verifier = mock(InternalRequestVerifier.class);
    private final SessionRegistry sessions = new SessionRegistry();
    private final InternalApiController controller = new InternalApiController(verifier, sessions, properties());

    @BeforeEach
    void authorizeRequests() {
        when(verifier.verify(any(), anyString())).thenReturn(Mono.just(true));
    }

    @Test
    void reportsTheConfiguredServerAndConnectedPlayers() {
        sessions.register(authenticated(new RecordingConnection("player"), 4, "Panda"));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/internal/v1/health/connection"));

        var response = controller.health(exchange).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("serverId", 17).containsEntry("players", 1);
    }

    @Test
    void kicksOnlyTheRequestedAuthenticatedSession() {
        RecordingConnection connection = new RecordingConnection("player");
        PlayerSession player = authenticated(connection, 4, "Panda");
        sessions.register(player);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/internal/v1/players/4/kick"));

        var response = controller.kick(4, "{}", exchange).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(connection.messages()).containsExactly("2;KICK_SHUTDOWN_MSG|");
        assertThat(connection.closed()).isTrue();
    }

    @Test
    void validatesAndDeliversBuddyStatusEvents() {
        RecordingConnection connection = new RecordingConnection("player");
        sessions.register(authenticated(connection, 4, "Panda"));
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/internal/v1/players/4/buddy-status"));

        var valid = controller.buddyStatus(4, "{\"buddyId\":9,\"status\":1}", exchange).block();
        var invalid = controller.buddyStatus(4, "{\"buddyId\":9,\"status\":7}", exchange).block();

        assertThat(valid.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(connection.messages()).containsExactly("61;9;1|");
    }

    @Test
    void notifiesOnlyTheOnlinePinboardOwner() {
        RecordingConnection connection = new RecordingConnection("player");
        sessions.register(authenticated(connection, 4, "Panda"));
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/internal/v1/players/4/pinboard-message"));

        var response = controller.pinboardMessage(4, "{}", exchange).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(connection.messages()).containsExactly("270|");
    }

    @Test
    void rejectsAnInvalidSignatureBeforePerformingAnAction() {
        when(verifier.verify(any(), anyString())).thenReturn(Mono.just(false));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/internal/v1/players/4/kick"));

        var response = controller.kick(4, "{}", exchange).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static GameServerProperties properties() {
        return new GameServerProperties(
                17,
                new GameServerProperties.Network("/game", 9595, true),
                new GameServerProperties.Security(List.of("http://localhost"), "secret", Duration.ofSeconds(30)),
                new GameServerProperties.Limits(8192, 64, 10, Duration.ofSeconds(30), Duration.ofMinutes(5)),
                new GameServerProperties.Rewards(true, Duration.ofSeconds(2), 100_000, 500));
    }
}
