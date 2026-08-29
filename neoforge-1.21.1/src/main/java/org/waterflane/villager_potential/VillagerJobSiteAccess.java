package org.waterflane.villager_potential;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Cheap, chunk-safe checks for the workstation currently owned by a villager. */
final class VillagerJobSiteAccess {
    private static final long VALIDATION_INTERVAL_TICKS = 20L;
    private static final Map<Villager, CachedJobSite> CACHE = new WeakHashMap<>();

    private VillagerJobSiteAccess() {
    }

    static boolean hasUsableJobSite(Villager villager, long gameTime) {
        return hasUsableJobSite(villager, gameTime, false);
    }

    static boolean hasUsableJobSite(Villager villager, long gameTime, boolean forceValidation) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) {
            forget(villager);
            return false;
        }

        var brain = villager.getBrain();
        if (brain == null) {
            // A real Villager always owns a brain; tolerate incomplete test/bootstrap doubles.
            return true;
        }
        Optional<GlobalPos> remembered = brain.getMemory(MemoryModuleType.JOB_SITE);
        if (remembered.isEmpty()) {
            CACHE.remove(villager);
            return false;
        }
        GlobalPos jobSite = remembered.orElseThrow();
        CachedJobSite cached = CACHE.get(villager);
        if (!forceValidation
                && cached != null
                && cached.profession() == profession
                && cached.position().equals(jobSite)
                && gameTime >= cached.checkedAt()
                && gameTime - cached.checkedAt() < VALIDATION_INTERVAL_TICKS) {
            return cached.valid();
        }

        boolean valid = validateWithoutLoadingChunks(villager, profession, jobSite);
        CACHE.put(villager, new CachedJobSite(profession, jobSite, valid, gameTime));
        return valid;
    }

    static void forget(Villager villager) {
        CACHE.remove(villager);
    }

    private static boolean validateWithoutLoadingChunks(
            Villager villager,
            VillagerProfession profession,
            GlobalPos jobSite
    ) {
        if (!(villager.level() instanceof ServerLevel villagerLevel)) {
            return false;
        }
        MinecraftServer server = villagerLevel.getServer();
        if (server == null) {
            // Unit-test and incomplete bootstrap environments have no live POI manager.
            return true;
        }
        ServerLevel jobLevel = server.getLevel(jobSite.dimension());
        if (jobLevel == null || !jobLevel.hasChunkAt(jobSite.pos())) {
            return false;
        }
        return jobLevel.getPoiManager().getType(jobSite.pos())
                .filter(profession.heldJobSite())
                .isPresent();
    }

    private record CachedJobSite(
            VillagerProfession profession,
            GlobalPos position,
            boolean valid,
            long checkedAt
    ) {
    }
}
