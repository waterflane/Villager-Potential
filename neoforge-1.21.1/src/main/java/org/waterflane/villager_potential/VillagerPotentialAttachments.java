package org.waterflane.villager_potential;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.waterflane.villager_potential.core.VillagerPotentialState;

public final class VillagerPotentialAttachments {
    static final Codec<VillagerPotentialState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("schema_version").forGetter(VillagerPotentialState::schemaVersion)
    ).apply(instance, VillagerPotentialState::new));

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
}
