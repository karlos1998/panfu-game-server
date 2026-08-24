package it.letscode.panfu.petrace;

import java.util.Set;

public record PetRaceMatch(int ticket, Set<Integer> playerIds) {

    public PetRaceMatch {
        playerIds = Set.copyOf(playerIds);
    }

    public boolean hasPlayer(int playerId) {
        return playerIds.contains(playerId);
    }

    public boolean hasBot() {
        return playerIds.contains(0);
    }
}
