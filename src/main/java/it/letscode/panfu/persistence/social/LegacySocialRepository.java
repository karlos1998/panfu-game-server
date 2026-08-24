package it.letscode.panfu.persistence.social;

public interface LegacySocialRepository {

    boolean debitCoins(int playerId, int amount);

    void updateProfileField(int playerId, String field, String value);
}
