package it.letscode.panfu.persistence.petrace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PetRaceProgressionTest {

    @Test
    void keepsProgressWithinTheCurrentLevelAndChargesRaceAndBoostHealth() {
        PetRacePet pet = pet(40, 1, 5, 1, 1, 1);

        PetRaceProgression.Result result = PetRaceProgression.apply(pet, 5, 2, List.of(501));

        assertThat(result.pet().experience()).isEqualTo(45);
        assertThat(result.pet().level()).isEqualTo(1);
        assertThat(result.pet().health()).isEqualTo(2);
        assertThat(result.levelIncreased()).isFalse();
        assertThat(result.abilities()).containsExactly(501);
        assertThat(PetRaceProgression.pointsForNextLevel(1)).isEqualTo(50);
        assertThat(PetRaceProgression.percentToNextLevel(45, 1)).isEqualTo(10);
    }

    @Test
    void levelUpAddsOneStatAndOneUniqueAbility() {
        PetRacePet pet = pet(90, 2, 5, 2, 2, 2);

        PetRaceProgression.Result result = PetRaceProgression.apply(pet, 10, 0, List.of(501));

        assertThat(result.pet().experience()).isEqualTo(100);
        assertThat(result.pet().level()).isEqualTo(3);
        assertThat(result.pet().speed() + result.pet().agility() + result.pet().power()).isEqualTo(7);
        assertThat(result.abilities()).hasSize(2).contains(501);
        assertThat(result.levelIncreased()).isTrue();
        assertThat(PetRaceProgression.pointsForNextLevel(3)).isEqualTo(150);
        assertThat(PetRaceProgression.percentToNextLevel(100, 3)).isEqualTo(100);
    }

    @Test
    void catchesUpMultipleLevelsWithoutDuplicatingAbilitiesOrExceedingStatCaps() {
        PetRacePet pet = pet(40, 1, 5, 5, 5, 4);

        PetRaceProgression.Result result = PetRaceProgression.apply(pet, 260, 9, List.of());

        assertThat(result.pet().level()).isEqualTo(7);
        assertThat(result.pet().health()).isZero();
        assertThat(result.pet().speed()).isEqualTo(5);
        assertThat(result.pet().agility()).isEqualTo(5);
        assertThat(result.pet().power()).isEqualTo(5);
        assertThat(result.abilities()).hasSize(7).doesNotHaveDuplicates().contains(501);
    }

    private PetRacePet pet(int experience, int level, int health, int speed, int agility, int power) {
        return new PetRacePet(77, 11, 2, "Bambus", true, health, speed, agility, power,
                experience, level, "[]");
    }
}
