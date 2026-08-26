package org.waterflane.villager_potential.core;

/**
 * Batches eligible loaded server ticks between persisted progression writes.
 *
 * <p>Platforms accumulate tenure one tick at a time and flush in intervals so
 * the persistent state is not rewritten every tick. The batch is a plain
 * accumulator: eligibility decisions (adulthood, job site, work activity)
 * remain platform-side, and the caller decides when to convert elapsed ticks
 * into {@link VillagerPotentialState#progressActiveProfession(long, long,
 * SkillProgressionConfig, ProfessionActivityConfig)}. The tracked profession
 * is nullable so platforms can hold a batch for villagers that are currently
 * unemployed.</p>
 */
public final class ProfessionProgressBatch {
    /** Eligible ticks accumulated before progression is flushed to state. */
    public static final long FLUSH_INTERVAL_TICKS = 20L;

    private final ProfessionId profession;
    private long elapsedProfessionTime;
    private long lastObservedGameTime;
    private int vanillaLevel;

    public ProfessionProgressBatch(
            ProfessionId profession,
            long elapsedProfessionTime,
            long lastObservedGameTime,
            int vanillaLevel
    ) {
        this.profession = profession;
        this.elapsedProfessionTime = elapsedProfessionTime;
        this.lastObservedGameTime = lastObservedGameTime;
        this.vanillaLevel = vanillaLevel;
    }

    public ProfessionId profession() {
        return profession;
    }

    public long elapsedProfessionTime() {
        return elapsedProfessionTime;
    }

    public long lastObservedGameTime() {
        return lastObservedGameTime;
    }

    public int vanillaLevel() {
        return vanillaLevel;
    }

    public void observeVanillaLevel(int level) {
        vanillaLevel = level;
    }

    public void observeGameTime(long gameTime) {
        lastObservedGameTime = Math.max(lastObservedGameTime, gameTime);
    }

    public void addElapsedTick() {
        elapsedProfessionTime++;
    }

    public void clearElapsedTime() {
        elapsedProfessionTime = 0L;
    }

    public boolean reachedFlushInterval() {
        return elapsedProfessionTime >= FLUSH_INTERVAL_TICKS;
    }
}
