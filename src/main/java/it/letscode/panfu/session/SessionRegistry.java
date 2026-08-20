package it.letscode.panfu.session;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public final class SessionRegistry {

    private final ConcurrentMap<Integer, PlayerSession> sessions = new ConcurrentHashMap<>();

    public boolean register(PlayerSession session) {
        if (!session.authenticated()) {
            throw new IllegalArgumentException("Only authenticated sessions can be registered");
        }
        return sessions.putIfAbsent(session.playerId(), session) == null;
    }

    public void remove(PlayerSession session) {
        if (session.authenticated()) {
            sessions.remove(session.playerId(), session);
        }
    }

    public Optional<PlayerSession> find(int playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public List<PlayerSession> all() {
        return sorted(sessions.values());
    }

    public List<PlayerSession> inRoom(PlayerSession source) {
        return inRoom(source.roomId(), source.home(), source.subRoom());
    }

    public List<PlayerSession> inRoom(int roomId, boolean home, int subRoom) {
        return sorted(sessions.values().stream()
                .filter(session -> session.roomId() == roomId)
                .filter(session -> session.home() == home)
                .filter(session -> session.subRoom() == subRoom)
                .toList());
    }

    public int size() {
        return sessions.size();
    }

    private List<PlayerSession> sorted(Collection<PlayerSession> values) {
        return values.stream().sorted(Comparator.comparingInt(PlayerSession::playerId)).toList();
    }
}
