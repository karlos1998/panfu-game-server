package it.letscode.panfu.security;

import java.time.Duration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public final class RedisNonceStore implements NonceStore {

    private final ReactiveStringRedisTemplate redis;

    public RedisNonceStore(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Boolean> claim(String nonce, Duration ttl) {
        return redis.opsForValue()
                .setIfAbsent("panfu:internal-api:nonce:" + nonce, "1", ttl)
                .defaultIfEmpty(false);
    }
}
