package it.letscode.panfu.persistence.reward;

import java.util.UUID;

public interface RewardLedgerRepository {

    RewardSettings settingsFor(int gameId);

    int awardOnce(UUID roundId, int playerId, int gameId, int score, int coins);
}
