package it.letscode.panfu.minigame;

import static org.assertj.core.api.Assertions.assertThat;

import it.letscode.panfu.config.GameServerProperties;
import it.letscode.panfu.persistence.reward.RewardSettings;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RewardPolicyTest {

    private final RewardPolicy policy = new RewardPolicy(new GameServerProperties(
            1,
            new GameServerProperties.Network("/game", 9595, true),
            new GameServerProperties.Security(List.of("http://localhost"), "secret", Duration.ofSeconds(30)),
            new GameServerProperties.Limits(8192, 64, 10, Duration.ofSeconds(30), Duration.ofMinutes(5)),
            new GameServerProperties.Rewards(true, Duration.ofSeconds(2), 100_000, 500)));
    private final RewardSettings settings = new RewardSettings(true, new BigDecimal("0.0500"), 200);

    @Test
    void calculatesConfiguredRewardAndCapsIt() {
        Instant started = Instant.parse("2026-01-01T00:00:00Z");

        assertThat(policy.calculate(1_000, started, started.plusSeconds(10), settings)).isEqualTo(50);
        assertThat(policy.calculate(99_999, started, started.plusSeconds(10), settings)).isEqualTo(200);
        assertThat(policy.calculate(
                        270,
                        started,
                        started.plusSeconds(10),
                        new RewardSettings(true, new BigDecimal("0.0500"), null)))
                .isEqualTo(13);
    }

    @Test
    void rejectsImpossibleOrInstantRounds() {
        Instant started = Instant.parse("2026-01-01T00:00:00Z");

        assertThat(policy.calculate(100_001, started, started.plusSeconds(10), settings)).isZero();
        assertThat(policy.calculate(1_000, started, started.plusMillis(500), settings)).isZero();
        assertThat(policy.calculate(-1, started, started.plusSeconds(10), settings)).isZero();
    }
}
