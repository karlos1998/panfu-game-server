package it.letscode.panfu.persistence.social;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcLegacySocialRepository implements LegacySocialRepository {

    private final JdbcClient jdbc;

    public JdbcLegacySocialRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public boolean debitCoins(int playerId, int amount) {
        if (amount <= 0) {
            return false;
        }
        return jdbc.sql("""
                        UPDATE users
                        SET coins = coins - :amount
                        WHERE id = :playerId AND coins >= :amount
                        """)
                .param("amount", amount)
                .param("playerId", playerId)
                .update() == 1;
    }

    @Override
    public void updateProfileField(int playerId, String field, String value) {
        if (!ProfileFields.ALLOWED.contains(field)) {
            throw new IllegalArgumentException("Unsupported profile field");
        }
        jdbc.sql("""
                        INSERT INTO player_profiles (user_id, %s, %s_checked, created_at, updated_at)
                        VALUES (:playerId, :value, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        ON DUPLICATE KEY UPDATE
                            %s = VALUES(%s),
                            %s_checked = true,
                            updated_at = CURRENT_TIMESTAMP
                        """.formatted(field, field, field, field, field))
                .param("playerId", playerId)
                .param("value", value)
                .update();
    }

    private static final class ProfileFields {
        private static final java.util.Set<String> ALLOWED = java.util.Set.of(
                "movie", "color", "hobby", "book", "song", "band", "school_subject", "sport",
                "animal", "rel_status", "motto", "best_char", "worst_char", "like_most", "like_least");
    }
}
