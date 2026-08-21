package it.letscode.panfu.persistence.chat;

@FunctionalInterface
public interface ChatMessageRepository {

    void record(int playerId, String playerName, int roomId, boolean home, String message);
}
