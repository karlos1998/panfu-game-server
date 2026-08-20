package it.letscode.panfu.persistence.reward;

import java.math.BigDecimal;

public record RewardSettings(boolean enabled, BigDecimal coinMultiplier, Integer maxCoinsPerRound) {}
