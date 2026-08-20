package it.letscode.panfu.persistence.reward;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcRewardLedgerRepository implements RewardLedgerRepository {

    private static final RewardSettings DEFAULTS = new RewardSettings(true, new BigDecimal("0.0500"), null);
    private final JdbcClient jdbc;

    public JdbcRewardLedgerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RewardSettings settingsFor(int gameId) {
        return jdbc.sql("""
                        SELECT enabled, coin_multiplier, max_coins_per_round
                        FROM minigame_rewards
                        WHERE game_id = :gameId
                        LIMIT 1
                        """)
                .param("gameId", gameId)
                .query((resultSet, row) -> {
                    Integer maximum = resultSet.getObject("max_coins_per_round", Integer.class);
                    return new RewardSettings(
                            resultSet.getBoolean("enabled"),
                            resultSet.getBigDecimal("coin_multiplier"),
                            maximum);
                })
                .optional()
                .orElse(DEFAULTS);
    }

    @Override
    @Transactional
    public int awardOnce(UUID roundId, int playerId, int gameId, int score, int coins) {
        if (coins <= 0) {
            return 0;
        }
        int claim = jdbc.sql("""
                        INSERT IGNORE INTO minigame_reward_claims
                            (round_id, user_id, game_id, score, coins, created_at, updated_at)
                        VALUES
                            (:roundId, :playerId, :gameId, :score, :coins, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .param("roundId", roundId.toString())
                .param("playerId", playerId)
                .param("gameId", gameId)
                .param("score", score)
                .param("coins", coins)
                .update();
        if (claim != 1) {
            return 0;
        }
        int updated = jdbc.sql("UPDATE users SET coins = COALESCE(coins, 0) + :coins WHERE id = :playerId")
                .param("coins", coins)
                .param("playerId", playerId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Reward recipient does not exist");
        }
        return coins;
    }
}
