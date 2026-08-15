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
import org.waterflane.villager_potential.core.ProfessionCareerState;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class VillagerPotentialAttachments {
    private static final ProfessionTenureEligibility TENURE_ELIGIBILITY =
            ProfessionTenureEligibility.ADULT;
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
    private static final Codec<ProfessionCareerState> CAREER_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("accumulated_profession_time")
                    .forGetter(ProfessionCareerState::accumulatedProfessionTime),
            Codec.DOUBLE.fieldOf("learned_skill").forGetter(ProfessionCareerState::learnedSkill),
            Codec.LONG.fieldOf("first_assignment").forGetter(ProfessionCareerState::firstAssignment),
            Codec.LONG.fieldOf("latest_assignment").forGetter(ProfessionCareerState::latestAssignment)
    ).apply(instance, ProfessionCareerState::new));
    private static final Codec<Map<ProfessionId, ProfessionCareerState>> CAREERS_CODEC =
            Codec.unboundedMap(PROFESSION_ID_CODEC, CAREER_CODEC);
    static final Codec<VillagerPotentialState> CODEC = RecordCodecBuilder.<PersistedState>create(instance -> instance.group(
            Codec.INT.fieldOf("schema_version").forGetter(PersistedState::schemaVersion),
            APTITUDES_CODEC.optionalFieldOf("aptitudes", Map.of()).forGetter(PersistedState::aptitudes),
            CAREERS_CODEC.optionalFieldOf("careers", Map.of()).forGetter(PersistedState::careers),
            PROFESSION_ID_CODEC.optionalFieldOf("active_profession")
                    .forGetter(PersistedState::activeProfession)
    ).apply(instance, PersistedState::new)).comapFlatMap(
            persisted -> migrate(
                    persisted.schemaVersion(),
                    persisted.aptitudes(),
                    persisted.careers(),
                    persisted.activeProfession()
            ),
            state -> new PersistedState(
                    state.schemaVersion(),
                    state.aptitudes(),
                    state.careers(),
                    state.activeProfession()
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

    static VillagerPotentialState trackProfession(Villager villager, long assignmentTime) {
        Objects.requireNonNull(villager, "villager");
        VillagerPotentialState state = get(villager);
        VillagerProfession profession = villager.getVillagerData().getProfession();
        VillagerPotentialState updatedState = profession == VillagerProfession.NONE
                || profession == VillagerProfession.NITWIT
                ? state.clearActiveProfession()
                : state.assignProfession(
                        VillagerProfessionIds.fromMinecraft(profession),
                        assignmentTime
                );

        if (updatedState.activeProfession().isPresent()
                && TENURE_ELIGIBILITY.canAccumulate(villager)) {
            updatedState = updatedState.accumulateActiveProfessionTime(1L);
        }

        if (updatedState != state) {
            villager.setData(POTENTIAL, updatedState);
        }
        return updatedState;
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
        if (!state.careers().isEmpty()) {
            initializedState = new VillagerPotentialState(
                    VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                    initializedState.aptitudes(),
                    state.careers(),
                    state.activeProfession()
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

    private static DataResult<VillagerPotentialState> migrate(
            int schemaVersion,
            Map<ProfessionId, Double> aptitudes,
            Map<ProfessionId, ProfessionCareerState> careers,
            Optional<ProfessionId> activeProfession
    ) {
        try {
            return DataResult.success(VillagerPotentialState.migrate(
                    schemaVersion,
                    aptitudes,
                    careers,
                    activeProfession
            ));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private record PersistedState(
            int schemaVersion,
            Map<ProfessionId, Double> aptitudes,
            Map<ProfessionId, ProfessionCareerState> careers,
            Optional<ProfessionId> activeProfession
    ) {
    }
}
