package org.waterflane.villager_potential;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Registration and persistence provider for villager Potential on Forge. */
@Mod.EventBusSubscriber(modid = Villager_potential.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VillagerPotentialCapabilities {
    public static final Capability<VillagerPotentialCapability> POTENTIAL =
            CapabilityManager.get(new CapabilityToken<>() { });
    private static final ResourceLocation KEY = new ResourceLocation(
            Villager_potential.MODID,
            "potential"
    );

    private VillagerPotentialCapabilities() {
    }

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        event.register(VillagerPotentialCapability.class);
    }

    @Mod.EventBusSubscriber(modid = Villager_potential.MODID)
    public static final class AttachmentEvents {
        private AttachmentEvents() {
        }

        @SubscribeEvent
        static void attach(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Villager
                    || event.getObject() instanceof ZombieVillager) {
                Provider provider = new Provider();
                event.addCapability(KEY, provider);
                event.addListener(provider::invalidate);
            }
        }
    }

    private static final class Provider
            implements ICapabilitySerializable<CompoundTag> {
        private final VillagerPotentialCapability capability = new VillagerPotentialCapability();
        private final LazyOptional<VillagerPotentialCapability> optional =
                LazyOptional.of(() -> capability);

        @Override
        public <T> @Nonnull LazyOptional<T> getCapability(
                @Nonnull Capability<T> requested,
                @Nullable Direction side
        ) {
            return POTENTIAL.orEmpty(requested, optional);
        }

        @Override
        public CompoundTag serializeNBT() {
            return capability.serialize();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            capability.deserialize(nbt);
        }

        void invalidate() {
            optional.invalidate();
        }
    }
}
