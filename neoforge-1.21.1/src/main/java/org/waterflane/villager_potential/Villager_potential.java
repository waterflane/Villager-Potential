package org.waterflane.villager_potential;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.waterflane.villager_potential.core.VillagerPotential;

@Mod(Villager_potential.MODID)
public class Villager_potential {
    public static final String MODID = VillagerPotential.MOD_ID;

    public Villager_potential(IEventBus modEventBus, ModContainer modContainer) {
        VanillaTradeClassifications.bootstrap();
        VillagerPotentialAttachments.register(modEventBus);

        modEventBus.addListener(ServerConfig::onConfigEvent);
        modEventBus.addListener(VillagerTradeProgressNetworking::registerPayloads);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
    }
}
