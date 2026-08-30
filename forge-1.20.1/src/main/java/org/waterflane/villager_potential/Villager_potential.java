package org.waterflane.villager_potential;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.waterflane.villager_potential.core.VillagerPotential;

/** Forge 1.20.1 entry point. */
@Mod(Villager_potential.MODID)
public final class Villager_potential {
    public static final String MODID = VillagerPotential.MOD_ID;

    public Villager_potential() {
        VanillaTradeClassifications.bootstrap();
        VillagerTradeProgressNetworking.register();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ServerConfig::onConfigEvent);
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.SERVER,
                ServerConfig.SPEC,
                MODID + "-server.toml"
        );
    }
}
