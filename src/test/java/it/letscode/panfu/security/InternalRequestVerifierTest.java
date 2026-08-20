package it.letscode.panfu.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.publisher.Mono;

class InternalRequestVerifierTest {

    private static final String SECRET = "test-secret";
    private static final Instant NOW = Instant.parse("2026-08-20T10:00:00Z");
    private final InMemoryNonceStore nonces = new InMemoryNonceStore();
    private final InternalRequestVerifier verifier = new InternalRequestVerifier(
            nonces, SECRET, Duration.ofSeconds(30), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsValidSignatureExactlyOnce() {
        String body = "{\"buddyId\":9,\"status\":1}";
        String nonce = "0123456789abcdef";
        String timestamp = Long.toString(NOW.getEpochSecond());
        String path = "/internal/v1/players/7/buddy-status";
        String signature = InternalRequestVerifier.sign(
                InternalRequestVerifier.canonical("POST", path, timestamp, nonce, body),
                SECRET.getBytes(StandardCharsets.UTF_8));
        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.POST, path)
                .header(InternalRequestVerifier.TIMESTAMP_HEADER, timestamp)
                .header(InternalRequestVerifier.NONCE_HEADER, nonce)
                .header(InternalRequestVerifier.SIGNATURE_HEADER, signature)
                .build();

        assertThat(verifier.verify(request, body).block()).isTrue();
        assertThat(verifier.verify(request, body).block()).isFalse();
    }

    @Test
    void rejectsExpiredTimestampAndModifiedBody() {
        String nonce = "fedcba9876543210";
        String timestamp = Long.toString(NOW.minusSeconds(31).getEpochSecond());
        String path = "/internal/v1/players/7/kick";
        String signature = InternalRequestVerifier.sign(
                InternalRequestVerifier.canonical("POST", path, timestamp, nonce, "{}"),
                SECRET.getBytes(StandardCharsets.UTF_8));
        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.POST, path)
                .header(InternalRequestVerifier.TIMESTAMP_HEADER, timestamp)
                .header(InternalRequestVerifier.NONCE_HEADER, nonce)
                .header(InternalRequestVerifier.SIGNATURE_HEADER, signature)
                .build();

        assertThat(verifier.verify(request, "{\"changed\":true}").block()).isFalse();
    }

    private static final class InMemoryNonceStore implements NonceStore {
        private final ConcurrentHashMap<String, Boolean> values = new ConcurrentHashMap<>();
        public Mono<Boolean> claim(String nonce, Duration ttl) {
            return Mono.just(values.putIfAbsent(nonce, true) == null);
        }
    }
}
