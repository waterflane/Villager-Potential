package org.waterflane.villager_potential.core;

import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic per-villager seed derivation shared by every loader.
 *
 * <p>All identity generation (initial aptitudes, inheritance, lazy aptitude
 * provisioning, and specialization assignment) draws its randomness from these
 * seeds, so identical worlds and villagers must produce identical results on
 * every supported Minecraft version and mod loader. Platforms supply the same
 * world seed and villager UUID and receive the same stream.</p>
 */
public final class PotentialSeeds {
    private static final long INITIALIZATION_SALT = 0x56494C4C41474552L;
    private static final long INHERITANCE_SALT = 0x494E484552495453L;
    private static final long SPECIALIZATION_SALT = 0x5350454349414C53L;
    private static final long LAZY_APTITUDE_SALT = 0x4150544954554445L;

    private PotentialSeeds() {
    }

    public static long initializationSeed(long worldSeed, UUID villagerId) {
        return mixedSeed(worldSeed, villagerId, INITIALIZATION_SALT);
    }

    public static long inheritanceSeed(long worldSeed, UUID villagerId) {
        return mixedSeed(worldSeed, villagerId, INHERITANCE_SALT);
    }

    public static long specializationSeed(
            long worldSeed,
            UUID villagerId,
            ProfessionId professionId
    ) {
        return mixedSeed(worldSeed, villagerId, professionSalt(SPECIALIZATION_SALT, professionId));
    }

    public static long lazyAptitudeSeed(
            long worldSeed,
            UUID villagerId,
            ProfessionId professionId
    ) {
        return mixedSeed(worldSeed, villagerId, professionSalt(LAZY_APTITUDE_SALT, professionId));
    }

    private static long professionSalt(long baseSalt, ProfessionId professionId) {
        Objects.requireNonNull(professionId, "professionId");
        long professionSalt = baseSalt;
        String profession = professionId.toString();
        for (int index = 0; index < profession.length(); index++) {
            professionSalt ^= profession.charAt(index);
            professionSalt *= 0x100000001B3L;
        }
        return professionSalt;
    }

    private static long mixedSeed(long worldSeed, UUID villagerId, long salt) {
        Objects.requireNonNull(villagerId, "villagerId");
        long seed = worldSeed
                ^ villagerId.getMostSignificantBits()
                ^ Long.rotateLeft(villagerId.getLeastSignificantBits(), 32)
                ^ salt;
        seed = (seed ^ (seed >>> 30)) * 0xBF58476D1CE4E5B9L;
        seed = (seed ^ (seed >>> 27)) * 0x94D049BB133111EBL;
        return seed ^ (seed >>> 31);
    }
}
