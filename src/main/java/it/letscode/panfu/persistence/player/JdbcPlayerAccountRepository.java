package it.letscode.panfu.persistence.player;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcPlayerAccountRepository implements PlayerAccountRepository {

    private final JdbcClient jdbc;

    public JdbcPlayerAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Optional<PlayerAccount> consumeLegacyTicket(int playerId, int ticket) {
        Optional<PlayerAccount> account = jdbc.sql("""
                        SELECT id, name, goldpanda, sheriff
                        FROM users
                        WHERE id = :playerId AND ticket_id = :ticket
                        LIMIT 1
                        FOR UPDATE
                        """)
                .param("playerId", playerId)
                .param("ticket", Integer.toString(ticket))
                .query((resultSet, row) -> new PlayerAccount(
                        resultSet.getInt("id"),
                        resultSet.getString("name"),
                        resultSet.getInt("goldpanda"),
                        resultSet.getInt("sheriff")))
                .optional();

        if (account.isEmpty()) {
            return Optional.empty();
        }

        int consumed = jdbc.sql("""
                        UPDATE users
                        SET ticket_id = NULL
                        WHERE id = :playerId AND ticket_id = :ticket
                        """)
                .param("playerId", playerId)
                .param("ticket", Integer.toString(ticket))
                .update();
        return consumed == 1 ? account : Optional.empty();
    }

    @Override
    public void markOnline(int playerId, int gameServerId) {
        jdbc.sql("UPDATE users SET current_gameserver = :serverId WHERE id = :playerId")
                .param("serverId", gameServerId)
                .param("playerId", playerId)
                .update();
    }

    @Override
    public void markOffline(int playerId, int gameServerId) {
        jdbc.sql("""
                        UPDATE users
                        SET current_gameserver = NULL
                        WHERE id = :playerId AND current_gameserver = :serverId
                        """)
                .param("playerId", playerId)
                .param("serverId", gameServerId)
                .update();
    }
}
