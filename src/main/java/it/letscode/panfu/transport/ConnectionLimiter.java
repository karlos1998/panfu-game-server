package it.letscode.panfu.transport;

import it.letscode.panfu.config.GameServerProperties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public final class ConnectionLimiter {

    private final ConcurrentMap<String, AtomicInteger> connections = new ConcurrentHashMap<>();
    private final int maximumPerIp;

    public ConnectionLimiter(GameServerProperties properties) {
        this.maximumPerIp = properties.limits().maxConnectionsPerIp();
    }

    public boolean acquire(String ip) {
        AtomicInteger count = connections.computeIfAbsent(ip, ignored -> new AtomicInteger());
        int value = count.incrementAndGet();
        if (value <= maximumPerIp) {
            return true;
        }
        release(ip);
        return false;
    }

    public void release(String ip) {
        connections.computeIfPresent(ip, (ignored, count) -> count.decrementAndGet() <= 0 ? null : count);
    }

    public int connectionsFor(String ip) {
        AtomicInteger count = connections.get(ip);
        return count == null ? 0 : count.get();
    }
}
