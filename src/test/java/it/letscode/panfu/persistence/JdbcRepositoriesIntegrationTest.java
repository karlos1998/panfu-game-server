package it.letscode.panfu.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import it.letscode.panfu.persistence.player.JdbcPlayerAccountRepository;
import it.letscode.panfu.persistence.reward.JdbcRewardLedgerRepository;
import it.letscode.panfu.persistence.server.JdbcGameServerStatusRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcRepositoriesIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("panfu")
            .withUsername("panfu")
            .withPassword("secret");

    private static HikariDataSource dataSource;
    private static JdbcClient jdbc;

    @BeforeAll
    static void createSchema() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MYSQL.getJdbcUrl());
        config.setUsername(MYSQL.getUsername());
        config.setPassword(MYSQL.getPassword());
        dataSource = new HikariDataSource(config);
        JdbcTemplate template = new JdbcTemplate(dataSource);
        jdbc = JdbcClient.create(template);
        template.execute("""
                CREATE TABLE users (
                    id INT PRIMARY KEY,
                    name VARCHAR(25) NOT NULL,
                    goldpanda INT NOT NULL DEFAULT 1,
                    sheriff INT NOT NULL DEFAULT 0,
                    coins INT NULL,
                    ticket_id VARCHAR(255) NULL,
                    current_gameserver INT NULL
                )
                """);
        template.execute("CREATE TABLE gameservers (id INT PRIMARY KEY, player_count INT NOT NULL DEFAULT 0)");
        template.execute("""
                CREATE TABLE minigame_rewards (
                    game_id INT PRIMARY KEY,
                    enabled BOOLEAN NOT NULL,
                    coin_multiplier DECIMAL(8, 4) NOT NULL,
                    max_coins_per_round INT NULL
                )
                """);
        template.execute("""
                CREATE TABLE minigame_reward_claims (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    round_id CHAR(36) NOT NULL UNIQUE,
                    user_id INT NOT NULL,
                    game_id INT NOT NULL,
                    score INT NOT NULL,
                    coins INT NOT NULL,
                    created_at TIMESTAMP NULL,
                    updated_at TIMESTAMP NULL
                )
                """);
        template.update("INSERT INTO users (id, name, ticket_id, coins) VALUES (7, 'Panda', '123456789', 10)");
        template.update("INSERT INTO gameservers (id, player_count) VALUES (1, 0)");
        template.update("""
                INSERT INTO minigame_rewards (game_id, enabled, coin_multiplier, max_coins_per_round)
                VALUES (5, true, 0.1000, 50)
                """);
        template.update("""
                INSERT INTO minigame_rewards (game_id, enabled, coin_multiplier, max_coins_per_round)
                VALUES (11, true, 0.0500, NULL)
                """);
    }

    @AfterAll
    static void closePool() {
        dataSource.close();
    }

    @Test
    void consumesLegacyTicketAndUpdatesPresence() {
        JdbcPlayerAccountRepository players = new JdbcPlayerAccountRepository(jdbc);
        JdbcGameServerStatusRepository servers = new JdbcGameServerStatusRepository(jdbc);

        assertThat(players.consumeLegacyTicket(7, 123456789)).hasValueSatisfying(player ->
                assertThat(player.username()).isEqualTo("Panda"));
        assertThat(players.consumeLegacyTicket(7, 123456789)).isEmpty();

        players.markOnline(7, 1);
        servers.updatePlayerCount(1, 1);
        assertThat(jdbc.sql("SELECT current_gameserver FROM users WHERE id = 7").query(Integer.class).single())
                .isEqualTo(1);
        players.markOffline(7, 1);
        assertThat(jdbc.sql("SELECT current_gameserver FROM users WHERE id = 7").query(Integer.class).optional())
                .isEmpty();
        assertThat(jdbc.sql("SELECT player_count FROM gameservers WHERE id = 1").query(Integer.class).single())
                .isEqualTo(1);
    }

    @Test
    void awardsEveryRoundAtMostOnce() {
        JdbcRewardLedgerRepository rewards = new JdbcRewardLedgerRepository(jdbc);
        UUID roundId = UUID.randomUUID();

        assertThat(rewards.settingsFor(5).coinMultiplier()).isEqualByComparingTo(new BigDecimal("0.1000"));
        assertThat(rewards.settingsFor(11).maxCoinsPerRound()).isNull();
        assertThat(rewards.awardOnce(roundId, 7, 5, 500, 50)).isEqualTo(50);
        assertThat(rewards.awardOnce(roundId, 7, 5, 500, 50)).isZero();
        assertThat(jdbc.sql("SELECT coins FROM users WHERE id = 7").query(Integer.class).single()).isEqualTo(60);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM minigame_reward_claims").query(Integer.class).single())
                .isEqualTo(1);
    }
}
