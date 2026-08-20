package it.letscode.panfu.persistence.server;

public interface GameServerStatusRepository {

    void updatePlayerCount(int gameServerId, int playerCount);
}
