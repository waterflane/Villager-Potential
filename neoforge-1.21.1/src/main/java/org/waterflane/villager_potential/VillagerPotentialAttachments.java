package org.waterflane.villager_potential;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.waterflane.villager_potential.core.AptitudeGenerationConfig;
import org.waterflane.villager_potential.core.AptitudeGenerator;
import org.waterflane.villager_potential.core.AptitudeInheritance;
import org.waterflane.villager_potential.core.AptitudeInheritanceConfig;
import org.waterflane.villager_potential.core.ProfessionActivityConfig;
import org.waterflane.villager_potential.core.ProfessionActivityState;
import org.waterflane.villager_potential.core.ProfessionCareerState;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.SkillProgression;
import org.waterflane.villager_potential.core.SkillProgressionConfig;
import org.waterflane.villager_potential.core.SpecializationId;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.random.RandomGenerator;

public final class VillagerPotentialAttachments {
    private static final ProfessionTenureEligibility TENURE_ELIGIBILITY =
            ProfessionTenureEligibility.ADULT;
    static final long PROFESSION_PROGRESS_INTERVAL_TICKS = 20L;
    static final SkillProgressionConfig SKILL_PROGRESSION_CONFIG = new SkillProgressionConfig(
            0.001,
            0.0,
            1.0,
            List.of(0.2, 0.5, 0.8, 1.0)
    );
    static final ProfessionActivityConfig PROFESSION_ACTIVITY_CONFIG =
            new ProfessionActivityConfig(0.5, 1.0, 2.0, 0.1, 0.0001);
    private static final Map<Villager, ProfessionProgressBatch> PROFESSION_PROGRESS_BATCHES =
            new WeakHashMap<>();
    static final AptitudeGenerationConfig APTITUDE_CONFIG = new AptitudeGenerationConfig(
            0.5,
            2.0,
            1.0,
            0.09,
            0.02
    );
    static final AptitudeInheritanceConfig INHERITANCE_CONFIG = new AptitudeInheritanceConfig(
            0.7,
            0.2,
            0.01
    );
    private static final long INITIALIZATION_SALT = 0x56494C4C41474552L;
    private static final long INHERITANCE_SALT = 0x494E484552495453L;
    private static final Codec<ProfessionId> PROFESSION_ID_CODEC = Codec.STRING.comapFlatMap(
            VillagerPotentialAttachments::parseProfessionId,
            ProfessionId::toString
    );
    private static final Codec<Map<ProfessionId, Double>> APTITUDES_CODEC = Codec.unboundedMap(
            PROFESSION_ID_CODEC,
            Codec.DOUBLE
    );
    private static final Codec<SpecializationId> SPECIALIZATION_ID_CODEC = Codec.STRING.comapFlatMap(
            VillagerPotentialAttachments::parseSpecializationId,
            SpecializationId::toString
    );
    private static final Codec<ProfessionCareerState> CAREER_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("accumulated_profession_time")
                    .forGetter(ProfessionCareerState::accumulatedProfessionTime),
            Codec.DOUBLE.fieldOf("learned_skill").forGetter(ProfessionCareerState::learnedSkill),
            Codec.LONG.fieldOf("first_assignment").forGetter(ProfessionCareerState::firstAssignment),
            Codec.LONG.fieldOf("latest_assignment").forGetter(ProfessionCareerState::latestAssignment),
            SPECIALIZATION_ID_CODEC.optionalFieldOf("specialization")
                    .forGetter(ProfessionCareerState::specialization)
    ).apply(instance, ProfessionCareerState::new));
    private static final Codec<Map<ProfessionId, ProfessionCareerState>> CAREERS_CODEC =
            Codec.unboundedMap(PROFESSION_ID_CODEC, CAREER_CODEC);
    private static final Codec<ProfessionActivityState> PROFESSION_ACTIVITY_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.DOUBLE.fieldOf("score").forGetter(ProfessionActivityState::score),
                    Codec.LONG.fieldOf("last_update_game_time")
                            .forGetter(ProfessionActivityState::lastUpdateGameTime)
            ).apply(instance, ProfessionActivityState::new));
    private static final Codec<Map<ProfessionId, ProfessionActivityState>>
            PROFESSION_ACTIVITIES_CODEC = Codec.unboundedMap(
                    PROFESSION_ID_CODEC,
                    PROFESSION_ACTIVITY_CODEC
            );
    static final Codec<VillagerPotentialState> CODEC = RecordCodecBuilder.<PersistedState>create(instance -> instance.group(
            Codec.INT.fieldOf("schema_version").forGetter(PersistedState::schemaVersion),
            APTITUDES_CODEC.optionalFieldOf("aptitudes", Map.of()).forGetter(PersistedState::aptitudes),
            CAREERS_CODEC.optionalFieldOf("careers", Map.of()).forGetter(PersistedState::careers),
            PROFESSION_ID_CODEC.optionalFieldOf("active_profession")
                    .forGetter(PersistedState::activeProfession),
            PROFESSION_ACTIVITIES_CODEC.optionalFieldOf("profession_activity", Map.of())
                    .forGetter(PersistedState::professionActivities)
    ).apply(instance, PersistedState::new)).comapFlatMap(
            persisted -> migrate(
                    persisted.schemaVersion(),
                    persisted.aptitudes(),
                    persisted.careers(),
                    persisted.activeProfession(),
                    persisted.professionActivities()
            ),
            state -> new PersistedState(
                    state.schemaVersion(),
                    state.aptitudes(),
                    state.careers(),
                    state.activeProfession(),
                    state.professionActivities()
            )
    );

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Villager_potential.MODID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<VillagerPotentialState>> POTENTIAL =
            ATTACHMENT_TYPES.register("potential", () -> AttachmentType.builder(VillagerPotentialState::createDefault)
                    .serialize(CODEC)
                    .copyOnDeath()
                    .copyHandler(VillagerPotentialAttachments::copyVillagerIdentity)
                    .build());

    private VillagerPotentialAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    public static VillagerPotentialState get(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        return getOrInitialize(villager);
    }

    static VillagerPotentialState get(ZombieVillager zombieVillager) {
        Objects.requireNonNull(zombieVillager, "zombieVillager");
        return getOrInitialize(zombieVillager);
    }

    static void trackProfession(Villager villager, long assignmentTime) {
        Objects.requireNonNull(villager, "villager");
        VillagerProfession profession = villager.getVillagerData().getProfession();
        ProfessionId currentProfession = toCareerProfession(profession);
        ProfessionProgressBatch batch = PROFESSION_PROGRESS_BATCHES.get(villager);
        VillagerPotentialState state = null;
        VillagerPotentialState updatedState = null;

        if (batch == null || !Objects.equals(batch.profession(), currentProfession)) {
            state = get(villager);
            updatedState = batch == null
                    ? state
                    : progressMatchingProfession(state, batch);
            updatedState = assignProfession(updatedState, currentProfession, assignmentTime);
            batch = new ProfessionProgressBatch(currentProfession, 0L, assignmentTime);
            PROFESSION_PROGRESS_BATCHES.put(villager, batch);
        }

        batch.observeGameTime(assignmentTime);

        if (currentProfession != null && TENURE_ELIGIBILITY.canAccumulate(villager)) {
            batch.addElapsedTick();
        }

        if (batch.elapsedProfessionTime() >= PROFESSION_PROGRESS_INTERVAL_TICKS) {
            if (state == null) {
                state = get(villager);
                updatedState = state;
            }
            updatedState = progressMatchingProfession(updatedState, batch);
            batch.clearElapsedTime();
        }

        if (state != null && updatedState != state) {
            villager.setData(POTENTIAL, updatedState);
        }
        if (updatedState != null) {
            queueEarnedProfessionLevel(villager, updatedState, currentProfession);
        }
    }

    static void flushProfessionProgress(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        ProfessionProgressBatch batch = PROFESSION_PROGRESS_BATCHES.remove(villager);
        if (batch == null || batch.elapsedProfessionTime() == 0L) {
            return;
        }

        VillagerPotentialState state = get(villager);
        VillagerPotentialState updatedState = progressMatchingProfession(state, batch);
        if (updatedState != state) {
            villager.setData(POTENTIAL, updatedState);
        }
    }

    static void recordTrade(Villager villager, long gameTime) {
        Objects.requireNonNull(villager, "villager");
        ProfessionId profession = toCareerProfession(
                villager.getVillagerData().getProfession()
        );
        if (profession == null) {
            return;
        }

        VillagerPotentialState state = get(villager);
        VillagerPotentialState updatedState = state.recordProfessionTrade(
                profession,
                gameTime,
                PROFESSION_ACTIVITY_CONFIG
        );
        if (updatedState != state) {
            villager.setData(POTENTIAL, updatedState);
        }
    }

    private static ProfessionId toCareerProfession(VillagerProfession profession) {
        return profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT
                ? null
                : VillagerProfessionIds.fromMinecraft(profession);
    }

    private static VillagerPotentialState assignProfession(
            VillagerPotentialState state,
            ProfessionId profession,
            long assignmentTime
    ) {
        return profession == null
                ? state.clearActiveProfession()
                : state.assignProfession(profession, assignmentTime);
    }

    private static VillagerPotentialState progressMatchingProfession(
            VillagerPotentialState state,
            ProfessionProgressBatch batch
    ) {
        if (batch.elapsedProfessionTime() == 0L
                || !state.activeProfession().equals(Optional.ofNullable(batch.profession()))) {
            return state;
        }
        return state.progressActiveProfession(
                batch.elapsedProfessionTime(),
                batch.lastObservedGameTime(),
                SKILL_PROGRESSION_CONFIG,
                PROFESSION_ACTIVITY_CONFIG
        );
    }

    /**
     * Queues at most one transition at a time. If one skill update crosses
     * several thresholds, subsequent transitions are queued only after vanilla
     * has applied the preceding level, so offers cannot be skipped or duplicated.
     */
    static boolean queueEarnedProfessionLevel(
            Villager villager,
            VillagerPotentialState state,
            ProfessionId profession
    ) {
        if (profession == null
                || !state.activeProfession().equals(Optional.of(profession))) {
            return false;
        }

        int currentLevel = villager.getVillagerData().getLevel();
        if (currentLevel < 1 || currentLevel >= 5) {
            return false;
        }

        double learnedSkill = state.careerFor(profession)
                .orElseThrow()
                .learnedSkill();
        int earnedLevel = SkillProgression.vanillaProfessionLevel(
                learnedSkill,
                currentLevel,
                SKILL_PROGRESSION_CONFIG
        );
        return earnedLevel > currentLevel
                && villager instanceof VillagerLevelUpAccess access
                && access.villagerPotential$queueLevelUp();
    }

    private static VillagerPotentialState getOrInitialize(Entity entity) {
        VillagerPotentialState state = entity.getData(POTENTIAL);
        if (!state.aptitudes().isEmpty()) {
            return state;
        }

        VillagerPotentialState initializedState = initialize(
                worldSeed(entity),
                entity.getUUID()
        );
        if (!state.careers().isEmpty() || !state.professionActivities().isEmpty()) {
            initializedState = new VillagerPotentialState(
                    VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                    initializedState.aptitudes(),
                    state.careers(),
                    state.activeProfession(),
                    state.professionActivities()
            );
        }
        entity.setData(POTENTIAL, initializedState);
        return initializedState;
    }

    /**
     * Potential is immutable, so retaining the complete state object is equivalent
     * to a serialize/deserialize copy and automatically includes later state fields.
     */
    static VillagerPotentialState copyVillagerIdentity(
            VillagerPotentialState state,
            IAttachmentHolder target,
            HolderLookup.Provider provider
    ) {
        return target instanceof Villager || target instanceof ZombieVillager ? state : null;
    }

    /**
     * Initializes a vanilla-bred child from its parents without replacing any
     * Potential that another breeding integration has already supplied.
     */
    public static VillagerPotentialState inherit(
            Villager firstParent,
            Villager secondParent,
            Villager child
    ) {
        Objects.requireNonNull(child, "child");
        return inherit(
                firstParent,
                secondParent,
                child,
                new Random(inheritanceSeed(worldSeed(child), child.getUUID()))
        );
    }

    static VillagerPotentialState inherit(
            Villager firstParent,
            Villager secondParent,
            Villager child,
            RandomGenerator random
    ) {
        Objects.requireNonNull(firstParent, "firstParent");
        Objects.requireNonNull(secondParent, "secondParent");
        Objects.requireNonNull(child, "child");
        Objects.requireNonNull(random, "random");

        VillagerPotentialState existingChildState = child.getData(POTENTIAL);
        if (!existingChildState.aptitudes().isEmpty()) {
            return existingChildState;
        }

        VillagerPotentialState inheritedState = AptitudeInheritance.inherit(
                get(firstParent),
                get(secondParent),
                VillagerProfessionIds.supportedVanillaProfessions(),
                APTITUDE_CONFIG,
                INHERITANCE_CONFIG,
                random
        );
        child.setData(POTENTIAL, inheritedState);
        return inheritedState;
    }

    static VillagerPotentialState initialize(long worldSeed, UUID villagerId) {
        Random random = new Random(initializationSeed(worldSeed, villagerId));
        Map<ProfessionId, Double> aptitudes = new LinkedHashMap<>();
        for (ProfessionId professionId : VillagerProfessionIds.supportedVanillaProfessions()) {
            aptitudes.put(professionId, AptitudeGenerator.generate(APTITUDE_CONFIG, random));
        }
        return new VillagerPotentialState(VillagerPotentialState.CURRENT_SCHEMA_VERSION, aptitudes);
    }

    private static long worldSeed(Entity entity) {
        return entity.level() instanceof ServerLevel serverLevel ? serverLevel.getSeed() : 0L;
    }

    private static long initializationSeed(long worldSeed, UUID villagerId) {
        return mixedSeed(worldSeed, villagerId, INITIALIZATION_SALT);
    }

    private static long inheritanceSeed(long worldSeed, UUID villagerId) {
        return mixedSeed(worldSeed, villagerId, INHERITANCE_SALT);
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

    private static DataResult<ProfessionId> parseProfessionId(String value) {
        try {
            return DataResult.success(ProfessionId.parse(value));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static DataResult<SpecializationId> parseSpecializationId(String value) {
        try {
            return DataResult.success(SpecializationId.parse(value));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static DataResult<VillagerPotentialState> migrate(
            int schemaVersion,
            Map<ProfessionId, Double> aptitudes,
            Map<ProfessionId, ProfessionCareerState> careers,
            Optional<ProfessionId> activeProfession,
            Map<ProfessionId, ProfessionActivityState> professionActivities
    ) {
        try {
            return DataResult.success(VillagerPotentialState.migrate(
                    schemaVersion,
                    aptitudes,
                    careers,
                    activeProfession,
                    professionActivities
            ));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private record PersistedState(
            int schemaVersion,
            Map<ProfessionId, Double> aptitudes,
            Map<ProfessionId, ProfessionCareerState> careers,
            Optional<ProfessionId> activeProfession,
            Map<ProfessionId, ProfessionActivityState> professionActivities
    ) {
    }

    private static final class ProfessionProgressBatch {
        private final ProfessionId profession;
        private long elapsedProfessionTime;
        private long lastObservedGameTime;

        private ProfessionProgressBatch(
                ProfessionId profession,
                long elapsedProfessionTime,
                long lastObservedGameTime
        ) {
            this.profession = profession;
            this.elapsedProfessionTime = elapsedProfessionTime;
            this.lastObservedGameTime = lastObservedGameTime;
        }

        private ProfessionId profession() {
            return profession;
        }

        private long elapsedProfessionTime() {
            return elapsedProfessionTime;
        }

        private long lastObservedGameTime() {
            return lastObservedGameTime;
        }

        private void observeGameTime(long gameTime) {
            lastObservedGameTime = Math.max(lastObservedGameTime, gameTime);
        }

        private void addElapsedTick() {
            elapsedProfessionTime++;
        }

        private void clearElapsedTime() {
            elapsedProfessionTime = 0L;
        }
    }
}
