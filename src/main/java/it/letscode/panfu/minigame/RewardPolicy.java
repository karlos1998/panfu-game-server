package it.letscode.panfu.minigame;

import it.letscode.panfu.config.GameServerProperties;
import it.letscode.panfu.persistence.reward.RewardSettings;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public final class RewardPolicy {

    private final GameServerProperties.Rewards limits;

    public RewardPolicy(GameServerProperties properties) {
        this.limits = properties.rewards();
    }

    public boolean serverAwardsEnabled() {
        return limits.serverAwardsEnabled();
    }

    public int calculate(int score, Instant startedAt, Instant finishedAt, RewardSettings settings) {
        if (!settings.enabled()
                || score <= 0
                || score > limits.maxScorePerRound()
                || startedAt == null
                || finishedAt.isBefore(startedAt)
                || Duration.between(startedAt, finishedAt).compareTo(limits.minimumRoundDuration()) < 0
                || settings.coinMultiplier() == null
                || settings.coinMultiplier().signum() <= 0) {
            return 0;
        }
        int coins = BigDecimal.valueOf(score)
                .multiply(settings.coinMultiplier())
                .setScale(0, RoundingMode.DOWN)
                .intValue();
        coins = Math.min(coins, limits.maxCoinsPerRound());
        if (settings.maxCoinsPerRound() != null) {
            coins = Math.min(coins, settings.maxCoinsPerRound());
        }
        return Math.max(0, coins);
    }
}
