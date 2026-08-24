package it.letscode.panfu.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import it.letscode.panfu.persistence.chat.JdbcChatMessageRepository;
import it.letscode.panfu.persistence.player.JdbcPlayerAccountRepository;
import it.letscode.panfu.persistence.petrace.JdbcPetRacePetRepository;
import it.letscode.panfu.persistence.reward.JdbcRewardLedgerRepository;
import it.letscode.panfu.persistence.server.JdbcGameServerStatusRepository;
import it.letscode.panfu.persistence.social.JdbcLegacySocialRepository;
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
        template.execute("""
                CREATE TABLE chat_messages (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NULL,
                    player_name VARCHAR(255) NOT NULL,
                    room_id INT UNSIGNED NOT NULL,
                    is_home BOOLEAN NOT NULL DEFAULT FALSE,
                    message VARCHAR(120) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        template.execute("""
                CREATE TABLE player_profiles (
                    user_id BIGINT PRIMARY KEY,
                    motto VARCHAR(160) NOT NULL DEFAULT '',
                    motto_checked BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMP NULL,
                    updated_at TIMESTAMP NULL
                )
                """);
        template.execute("""
                CREATE TABLE pokopets (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    type INT NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    selected BOOLEAN NOT NULL DEFAULT FALSE,
                    health INT NOT NULL,
                    speed INT NOT NULL,
                    agility INT NOT NULL,
                    power INT NOT NULL,
                    experience INT NOT NULL,
                    level INT NOT NULL,
                    abilities JSON NULL
                )
                """);
        template.update("INSERT INTO users (id, name, ticket_id, coins) VALUES (7, 'Panda', '123456789', 10)");
        template.update("INSERT INTO gameservers (id, player_count) VALUES (1, 0)");
        template.update("""
                INSERT INTO pokopets
                    (id, user_id, type, name, selected, health, speed, agility, power, experience, level, abilities)
                VALUES (77, 7, 2, 'Bambus', true, 5, 3, 2, 1, 40, 2, '[101]')
                """);
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

    @Test
    void recordsChatMessageContext() {
        JdbcChatMessageRepository messages = new JdbcChatMessageRepository(jdbc);

        messages.record(7, "Panda", 13, false, "Hello Castle");

        assertThat(jdbc.sql("""
                        SELECT CONCAT(user_id, '|', player_name, '|', room_id, '|', is_home, '|', message)
                        FROM chat_messages
                        """)
                .query(String.class)
                .single()).isEqualTo("7|Panda|13|0|Hello Castle");
    }

    @Test
    void atomicallyChargesEcardsAndUpsertsProfileFields() {
        JdbcLegacySocialRepository social = new JdbcLegacySocialRepository(jdbc);
        jdbc.sql("UPDATE users SET coins = 20 WHERE id = 7").update();

        assertThat(social.debitCoins(7, 10)).isTrue();
        assertThat(social.debitCoins(7, 11)).isFalse();
        social.updateProfileField(7, "motto", "Kocham Panfu");
        social.updateProfileField(7, "motto", "Jeszcze bardziej");

        assertThat(jdbc.sql("SELECT coins FROM users WHERE id = 7").query(Integer.class).single()).isEqualTo(10);
        assertThat(jdbc.sql("SELECT motto FROM player_profiles WHERE user_id = 7")
                .query(String.class).single()).isEqualTo("Jeszcze bardziej");
    }

    @Test
    void loadsThePetRaceSnapshotFromTheLegacyPokopetTable() {
        JdbcPetRacePetRepository pets = new JdbcPetRacePetRepository(jdbc);

        assertThat(pets.find(77)).hasValueSatisfying(pet -> {
            assertThat(pet.ownerId()).isEqualTo(7);
            assertThat(pet.name()).isEqualTo("Bambus");
            assertThat(pet.selected()).isTrue();
            assertThat(pet.health()).isEqualTo(5);
            assertThat(pet.abilitiesJson()).isEqualTo("[101]");
        });
        assertThat(pets.find(999)).isEmpty();

        assertThat(pets.applyRaceResult(77, 999, 20)).isEmpty();
        assertThat(pets.applyRaceResult(77, 7, 20)).hasValueSatisfying(pet -> {
            assertThat(pet.health()).isEqualTo(4);
            assertThat(pet.experience()).isEqualTo(60);
        });
    }
}
