package it.letscode.panfu.transport;

import static org.assertj.core.api.Assertions.assertThat;

import it.letscode.panfu.persistence.player.PlayerAccountRepository;
import it.letscode.panfu.persistence.reward.RewardLedgerRepository;
import it.letscode.panfu.persistence.server.GameServerStatusRepository;
import it.letscode.panfu.transport.tcp.LegacyTcpServer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;
import reactor.netty.tcp.TcpClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "panfu.game-server.network.legacy-tcp-port=0",
            "panfu.game-server.security.allowed-origins=http://localhost",
            "management.health.db.enabled=false",
            "management.health.redis.enabled=false"
        })
class TransportIntegrationTest {

    @LocalServerPort
    private int httpPort;

    @Autowired
    private LegacyTcpServer legacyTcpServer;

    @MockitoBean
    private PlayerAccountRepository players;

    @MockitoBean
    private GameServerStatusRepository servers;

    @MockitoBean
    private RewardLedgerRepository rewards;

    @Test
    void websocketAndTcpUseTheSameProtocolPipeline() {
        AtomicReference<String> websocketResponse = new AtomicReference<>();
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost");
        new ReactorNettyWebSocketClient().execute(
                        URI.create("ws://127.0.0.1:" + httpPort + "/game"),
                        headers,
                        session -> Mono.when(
                                session.send(Mono.just(session.binaryMessage(factory ->
                                        factory.wrap("301|".getBytes(StandardCharsets.UTF_8))))),
                                session.receive()
                                        .next()
                                        .doOnNext(message -> websocketResponse.set(message.getPayloadAsText()))
                                        .then()))
                .timeout(Duration.ofSeconds(5))
                .block();

        AtomicReference<String> tcpResponse = new AtomicReference<>();
        TcpClient.create()
                .host("127.0.0.1")
                .port(legacyTcpServer.boundPort())
                .handle((inbound, outbound) -> outbound.sendString(Mono.just("301|"))
                        .then()
                        .thenMany(inbound.receive().asString().take(1)
                                .doOnNext(tcpResponse::set))
                        .then())
                .connectNow(Duration.ofSeconds(5))
                .onDispose()
                .block(Duration.ofSeconds(5));

        String expected = "301;P4nfu8Ri5$3*m/#4nt1Ch34t2gHTu.%ru1{<0?K_&45fS4lt6,]-lO5=+354y|";
        assertThat(websocketResponse.get()).isEqualTo(expected);
        assertThat(tcpResponse.get()).isEqualTo(expected);
    }
}
