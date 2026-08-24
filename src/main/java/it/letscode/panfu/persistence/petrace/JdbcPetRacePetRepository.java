package it.letscode.panfu.persistence.petrace;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPetRacePetRepository implements PetRacePetRepository {

    private final JdbcClient jdbc;

    public JdbcPetRacePetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PetRacePet> find(int petId) {
        return jdbc.sql("""
                        SELECT id, user_id, type, name, selected, health, speed, agility, power, experience, level, abilities
                        FROM pokopets
                        WHERE id = :petId
                        LIMIT 1
                        """)
                .param("petId", petId)
                .query((resultSet, row) -> new PetRacePet(
                        resultSet.getInt("id"),
                        resultSet.getInt("user_id"),
                        resultSet.getInt("type"),
                        resultSet.getString("name"),
                        resultSet.getBoolean("selected"),
                        resultSet.getInt("health"),
                        resultSet.getInt("speed"),
                        resultSet.getInt("agility"),
                        resultSet.getInt("power"),
                        resultSet.getInt("experience"),
                        resultSet.getInt("level"),
                        resultSet.getString("abilities")))
                .optional();
    }

    @Override
    public Optional<PetRacePet> applyRaceResult(int petId, int ownerId, int experienceReward) {
        int updated = jdbc.sql("""
                        UPDATE pokopets
                        SET health = CASE WHEN health > 0 THEN health - 1 ELSE 0 END,
                            experience = experience + :experienceReward
                        WHERE id = :petId
                          AND user_id = :ownerId
                        """)
                .param("experienceReward", experienceReward)
                .param("petId", petId)
                .param("ownerId", ownerId)
                .update();

        if (updated != 1) {
            return Optional.empty();
        }

        return find(petId).filter(pet -> pet.ownerId() == ownerId);
    }
}
