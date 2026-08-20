package it.letscode.panfu.security;

import it.letscode.panfu.config.GameServerProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public final class InternalRequestVerifier {

    public static final String TIMESTAMP_HEADER = "X-Panfu-Timestamp";
    public static final String NONCE_HEADER = "X-Panfu-Nonce";
    public static final String SIGNATURE_HEADER = "X-Panfu-Signature";
    private final NonceStore nonces;
    private final byte[] secret;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public InternalRequestVerifier(NonceStore nonces, GameServerProperties properties) {
        this(nonces, properties.security().internalApiSecret(), properties.security().internalRequestTtl(), Clock.systemUTC());
    }

    InternalRequestVerifier(NonceStore nonces, String secret, Duration ttl, Clock clock) {
        this.nonces = nonces;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl;
        this.clock = clock;
    }

    public Mono<Boolean> verify(ServerHttpRequest request, String body) {
        HttpHeaders headers = request.getHeaders();
        String timestamp = headers.getFirst(TIMESTAMP_HEADER);
        String nonce = headers.getFirst(NONCE_HEADER);
        String suppliedSignature = headers.getFirst(SIGNATURE_HEADER);
        if (timestamp == null || nonce == null || suppliedSignature == null
                || nonce.length() < 16 || nonce.length() > 128) {
            return Mono.just(false);
        }

        long epoch;
        try {
            epoch = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            return Mono.just(false);
        }
        Duration age = Duration.between(Instant.ofEpochSecond(epoch), clock.instant()).abs();
        if (age.compareTo(ttl) > 0) {
            return Mono.just(false);
        }

        String canonical = canonical(
                request.getMethod().name(),
                request.getPath().value(),
                timestamp,
                nonce,
                body == null ? "" : body);
        byte[] expected = HexFormat.of().parseHex(sign(canonical, secret));
        byte[] supplied;
        try {
            supplied = HexFormat.of().parseHex(suppliedSignature);
        } catch (IllegalArgumentException exception) {
            return Mono.just(false);
        }
        if (!MessageDigest.isEqual(expected, supplied)) {
            return Mono.just(false);
        }
        return nonces.claim(nonce, ttl);
    }

    public static String canonical(String method, String path, String timestamp, String nonce, String body) {
        return String.join("\n", method, path, timestamp, nonce, sha256(body));
    }

    public static String sign(String canonical, byte[] secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not calculate internal API signature", exception);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not calculate request body hash", exception);
        }
    }
}
