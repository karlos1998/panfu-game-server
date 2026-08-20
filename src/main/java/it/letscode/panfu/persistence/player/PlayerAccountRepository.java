package it.letscode.panfu.persistence.player;

import java.util.Optional;

public interface PlayerAccountRepository {

    Optional<PlayerAccount> consumeLegacyTicket(int playerId, int ticket);

    void markOnline(int playerId, int gameServerId);

    void markOffline(int playerId, int gameServerId);
}
