package it.letscode.panfu.persistence.chat;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcChatMessageRepository implements ChatMessageRepository {

    private final JdbcClient jdbc;

    public JdbcChatMessageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(int playerId, String playerName, int roomId, boolean home, String message) {
        jdbc.sql("""
                        INSERT INTO chat_messages
                            (user_id, player_name, room_id, is_home, message, created_at)
                        VALUES
                            (:playerId, :playerName, :roomId, :home, :message, CURRENT_TIMESTAMP)
                        """)
                .param("playerId", playerId)
                .param("playerName", playerName)
                .param("roomId", roomId)
                .param("home", home)
                .param("message", message)
                .update();
    }
}
