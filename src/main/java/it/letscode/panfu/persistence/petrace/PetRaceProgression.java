package it.letscode.panfu.persistence.petrace;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PetRaceProgression {

    public static final int EXPERIENCE_PER_LEVEL = 50;
    private static final int MAX_STAT = 5;
    private static final int BOOST_ABILITY = 501;
    private static final List<Integer> LEVEL_ABILITIES = java.util.stream.IntStream.rangeClosed(502, 523)
            .boxed()
            .toList();

    private PetRaceProgression() {}

    public static Result apply(PetRacePet pet, int experienceReward, int boostUses, List<Integer> currentAbilities) {
        int experience = pet.experience() + Math.max(0, experienceReward);
        int level = Math.max(1, pet.level());
        int speed = pet.speed();
        int agility = pet.agility();
        int power = pet.power();
        Set<Integer> abilities = new LinkedHashSet<>(currentAbilities);
        if (abilities.isEmpty()) {
            abilities.add(BOOST_ABILITY);
        }

        boolean levelIncreased = false;
        while (experience >= pointsForNextLevel(level)) {
            level++;
            levelIncreased = true;
            int stat = Math.floorMod(pet.id() + level, 3);
            for (int attempt = 0; attempt < 3; attempt++) {
                int candidate = (stat + attempt) % 3;
                if (candidate == 0 && speed < MAX_STAT) {
                    speed++;
                    break;
                }
                if (candidate == 1 && agility < MAX_STAT) {
                    agility++;
                    break;
                }
                if (candidate == 2 && power < MAX_STAT) {
                    power++;
                    break;
                }
            }
            addLevelAbility(abilities, pet.id(), level);
        }

        int healthCost = 1 + Math.max(0, boostUses);
        PetRacePet updated = new PetRacePet(
                pet.id(), pet.ownerId(), pet.type(), pet.name(), pet.selected(),
                Math.max(0, pet.health() - healthCost), speed, agility, power,
                experience, level, "");
        return new Result(updated, List.copyOf(abilities), levelIncreased);
    }

    public static int pointsForNextLevel(int level) {
        return Math.max(1, level) * EXPERIENCE_PER_LEVEL;
    }

    public static int percentToNextLevel(int experience, int level) {
        int safeLevel = Math.max(1, level);
        int previous = (safeLevel - 1) * EXPERIENCE_PER_LEVEL;
        int next = pointsForNextLevel(safeLevel);
        int earned = Math.max(0, Math.min(EXPERIENCE_PER_LEVEL, experience - previous));
        return 100 - (earned * 100 / EXPERIENCE_PER_LEVEL);
    }

    private static void addLevelAbility(Set<Integer> abilities, int petId, int level) {
        int start = Math.floorMod(petId * 31 + level * 17, LEVEL_ABILITIES.size());
        for (int offset = 0; offset < LEVEL_ABILITIES.size(); offset++) {
            int ability = LEVEL_ABILITIES.get((start + offset) % LEVEL_ABILITIES.size());
            if (abilities.add(ability)) {
                return;
            }
        }
    }

    public record Result(PetRacePet pet, List<Integer> abilities, boolean levelIncreased) {
        public Result {
            abilities = List.copyOf(new ArrayList<>(abilities));
        }
    }
}
