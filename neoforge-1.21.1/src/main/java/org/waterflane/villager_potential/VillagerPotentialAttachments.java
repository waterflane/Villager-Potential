package org.waterflane.villager_potential;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.waterflane.villager_potential.core.AptitudeGenerationConfig;
import org.waterflane.villager_potential.core.AptitudeGenerator;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public final class VillagerPotentialAttachments {
    private static final AptitudeGenerationConfig APTITUDE_CONFIG = new AptitudeGenerationConfig(
            0.5,
            2.0,
            1.0,
            0.09,
            0.02
    );
    private static final long INITIALIZATION_SALT = 0x56494C4C41474552L;
    private static final Codec<ProfessionId> PROFESSION_ID_CODEC = Codec.STRING.comapFlatMap(
            VillagerPotentialAttachments::parseProfessionId,
            ProfessionId::toString
    );
    private static final Codec<Map<ProfessionId, Double>> APTITUDES_CODEC = Codec.unboundedMap(
            PROFESSION_ID_CODEC,
            Codec.DOUBLE
    );
    static final Codec<VillagerPotentialState> CODEC = RecordCodecBuilder.<PersistedState>create(instance -> instance.group(
            Codec.INT.fieldOf("schema_version").forGetter(PersistedState::schemaVersion),
            APTITUDES_CODEC.optionalFieldOf("aptitudes", Map.of()).forGetter(PersistedState::aptitudes)
    ).apply(instance, PersistedState::new)).comapFlatMap(
            persisted -> migrate(persisted.schemaVersion(), persisted.aptitudes()),
            state -> new PersistedState(state.schemaVersion(), state.aptitudes())
    );

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Villager_potential.MODID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<VillagerPotentialState>> POTENTIAL =
            ATTACHMENT_TYPES.register("potential", () -> AttachmentType.builder(VillagerPotentialState::createDefault)
                    .serialize(CODEC)
                    .build());

    private VillagerPotentialAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    public static VillagerPotentialState get(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        VillagerPotentialState state = villager.getData(POTENTIAL);
        if (!state.aptitudes().isEmpty()) {
            return state;
        }

        VillagerPotentialState initializedState = initialize(
                worldSeed(villager),
                villager.getUUID()
        );
        villager.setData(POTENTIAL, initializedState);
        return initializedState;
    }

    static VillagerPotentialState initialize(long worldSeed, UUID villagerId) {
        Random random = new Random(initializationSeed(worldSeed, villagerId));
        Map<ProfessionId, Double> aptitudes = new LinkedHashMap<>();
        for (ProfessionId professionId : VillagerProfessionIds.supportedVanillaProfessions()) {
            aptitudes.put(professionId, AptitudeGenerator.generate(APTITUDE_CONFIG, random));
        }
        return new VillagerPotentialState(VillagerPotentialState.CURRENT_SCHEMA_VERSION, aptitudes);
    }

    private static long worldSeed(Villager villager) {
        return villager.level() instanceof ServerLevel serverLevel ? serverLevel.getSeed() : 0L;
    }

    private static long initializationSeed(long worldSeed, UUID villagerId) {
        Objects.requireNonNull(villagerId, "villagerId");
        long seed = worldSeed
                ^ villagerId.getMostSignificantBits()
                ^ Long.rotateLeft(villagerId.getLeastSignificantBits(), 32)
                ^ INITIALIZATION_SALT;
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
            Map<ProfessionId, Double> aptitudes
    ) {
        try {
            return DataResult.success(VillagerPotentialState.migrate(schemaVersion, aptitudes));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private record PersistedState(int schemaVersion, Map<ProfessionId, Double> aptitudes) {
    }
}
