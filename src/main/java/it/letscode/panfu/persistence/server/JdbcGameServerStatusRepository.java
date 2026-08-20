package it.letscode.panfu.persistence.server;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcGameServerStatusRepository implements GameServerStatusRepository {

    private final JdbcClient jdbc;

    public JdbcGameServerStatusRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void updatePlayerCount(int gameServerId, int playerCount) {
        jdbc.sql("UPDATE gameservers SET player_count = :count WHERE id = :serverId")
                .param("count", playerCount)
                .param("serverId", gameServerId)
                .update();
    }
}
