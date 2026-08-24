package it.letscode.panfu.persistence.petrace;

import java.util.Optional;

public interface PetRacePetRepository {

    Optional<PetRacePet> find(int petId);

    Optional<PetRacePet> applyRaceResult(int petId, int ownerId, int experienceReward, int boostUses);
}
