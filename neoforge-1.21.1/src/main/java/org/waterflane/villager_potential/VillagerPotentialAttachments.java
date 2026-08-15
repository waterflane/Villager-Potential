package org.waterflane.villager_potential;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.Map;

public final class VillagerPotentialAttachments {
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
        return villager.getData(POTENTIAL);
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
