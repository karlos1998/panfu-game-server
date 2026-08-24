package it.letscode.panfu.persistence.petrace;

public record PetRacePet(
        int id,
        int ownerId,
        int type,
        String name,
        boolean selected,
        int health,
        int speed,
        int agility,
        int power,
        int experience,
        int level,
        String abilitiesJson) {}
