package it.letscode.panfu.security;

import java.time.Duration;
import reactor.core.publisher.Mono;

public interface NonceStore {

    Mono<Boolean> claim(String nonce, Duration ttl);
}
