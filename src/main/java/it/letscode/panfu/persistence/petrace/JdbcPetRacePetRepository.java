package it.letscode.panfu.persistence.petrace;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcPetRacePetRepository implements PetRacePetRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public JdbcPetRacePetRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
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
    @Transactional
    public Optional<PetRacePet> applyRaceResult(int petId, int ownerId, int experienceReward, int boostUses) {
        PetRacePet current = jdbc.sql("""
                        SELECT id, user_id, type, name, selected, health, speed, agility, power, experience, level, abilities
                        FROM pokopets
                        WHERE id = :petId
                          AND user_id = :ownerId
                        FOR UPDATE
                        """)
                .param("petId", petId)
                .param("ownerId", ownerId)
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
                .optional()
                .orElse(null);
        if (current == null) {
            return Optional.empty();
        }

        PetRaceProgression.Result result = PetRaceProgression.apply(
                current, experienceReward, boostUses, abilities(current.abilitiesJson()));
        PetRacePet pet = result.pet();
        int updated = jdbc.sql("""
                        UPDATE pokopets
                        SET health = :health,
                            speed = :speed,
                            agility = :agility,
                            power = :power,
                            experience = :experience,
                            level = :level,
                            abilities = :abilities
                        WHERE id = :petId
                          AND user_id = :ownerId
                        """)
                .param("health", pet.health())
                .param("speed", pet.speed())
                .param("agility", pet.agility())
                .param("power", pet.power())
                .param("experience", pet.experience())
                .param("level", pet.level())
                .param("abilities", encodeAbilities(result.abilities()))
                .param("petId", petId)
                .param("ownerId", ownerId)
                .update();

        if (updated != 1) {
            return Optional.empty();
        }

        return find(petId).filter(savedPet -> savedPet.ownerId() == ownerId);
    }

    private java.util.List<Integer> abilities(String value) {
        if (value == null || value.isBlank()) {
            return java.util.List.of();
        }
        try {
            JsonNode node = json.readTree(value);
            if (!node.isArray()) {
                return java.util.List.of();
            }
            java.util.List<Integer> abilities = new java.util.ArrayList<>();
            node.forEach(entry -> abilities.add(entry.asInt()));
            return java.util.List.copyOf(abilities);
        } catch (JacksonException ignored) {
            return java.util.List.of();
        }
    }

    private String encodeAbilities(java.util.List<Integer> abilities) {
        try {
            return json.writeValueAsString(abilities);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not encode pet race abilities", exception);
        }
    }
}
