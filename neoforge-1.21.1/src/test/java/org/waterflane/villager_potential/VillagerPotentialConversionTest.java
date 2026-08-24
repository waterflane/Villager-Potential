package org.waterflane.villager_potential;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.MarketDemandState;
import org.waterflane.villager_potential.core.ProfessionActivityState;
import org.waterflane.villager_potential.core.ProfessionCareerState;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.SpecializationId;
import org.waterflane.villager_potential.core.TradeHistory;
import org.waterflane.villager_potential.core.TradeKey;
import org.waterflane.villager_potential.core.TradePaletteState;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VillagerPotentialConversionTest {
    private static final long WORLD_SEED = 42L;
    private static final ProfessionId FARMER = ProfessionId.parse("minecraft:farmer");
    private static final TradeKey WHEAT = new TradeKey.Offer(
            new TradeKey.Item("minecraft:wheat", 20),
            new TradeKey.Item("minecraft:emerald", 1)
    );

    @Test
    void villagerZombieVillagerVillagerRetainsCompletePotential() {
        VillagerPotentialState originalState = state(1.37);
        Villager original = carrier(Villager.class, originalState, uuid(1));

        VillagerPotentialEvents.onLivingConversionPre(pre(original, EntityType.ZOMBIE_VILLAGER));
        VillagerPotentialState zombieState = VillagerPotentialAttachments.copyVillagerIdentity(
                originalState,
                mock(ZombieVillager.class),
                mock(HolderLookup.Provider.class)
        );
        ZombieVillager zombie = carrier(ZombieVillager.class, zombieState, uuid(2));

        VillagerPotentialEvents.onLivingConversionPre(pre(zombie, EntityType.VILLAGER));
        VillagerPotentialState restoredState = VillagerPotentialAttachments.copyVillagerIdentity(
                zombieState,
                mock(Villager.class),
                mock(HolderLookup.Provider.class)
        );

        assertSame(originalState, zombieState);
        assertSame(originalState, restoredState);
        assertEquals(1.37, restoredState.aptitudeFor(FARMER).orElseThrow());
        assertEquals(originalState.careers(), restoredState.careers());
        assertEquals(originalState.professionActivities(), restoredState.professionActivities());
        assertEquals(originalState.tradePalettes(), restoredState.tradePalettes());
        assertEquals(originalState.marketDemand(), restoredState.marketDemand());
        verify(original, times(0)).setData(any(Supplier.class), any());
        verify(zombie, times(0)).setData(any(Supplier.class), any());
    }

    @Test
    void zombieVillagerPotentialSurvivesSaveLoadBeforeCuring() {
        VillagerPotentialState convertedState = state(0.83);
        Tag saved = VillagerPotentialAttachments.CODEC
                .encodeStart(NbtOps.INSTANCE, convertedState)
                .getOrThrow();
        VillagerPotentialState loadedState = VillagerPotentialAttachments.CODEC
                .parse(NbtOps.INSTANCE, saved)
                .getOrThrow();
        ZombieVillager loadedZombie = carrier(ZombieVillager.class, loadedState, uuid(3));

        VillagerPotentialEvents.onLivingConversionPre(pre(loadedZombie, EntityType.VILLAGER));
        VillagerPotentialState curedState = VillagerPotentialAttachments.copyVillagerIdentity(
                loadedState,
                mock(Villager.class),
                mock(HolderLookup.Provider.class)
        );

        assertEquals(convertedState, loadedState);
        assertSame(loadedState, curedState);
        verify(loadedZombie, times(0)).setData(any(Supplier.class), any());
    }

    @Test
    void curedOutcomeDoesNotGenerateBeforeTransferredStateArrives() {
        VillagerPotentialState transferredState = state(1.21);
        AtomicReference<VillagerPotentialState> curedData = new AtomicReference<>();
        Villager curedVillager = carrierWithStateReference(Villager.class, curedData, uuid(5));

        VillagerPotentialEvents.onEntityJoinLevel(new EntityJoinLevelEvent(
                curedVillager,
                curedVillager.level(),
                false
        ));

        verify(curedVillager, times(0)).getData(any(Supplier.class));
        verify(curedVillager, times(0)).setData(any(Supplier.class), any());

        curedData.set(VillagerPotentialAttachments.copyVillagerIdentity(
                transferredState,
                curedVillager,
                mock(HolderLookup.Provider.class)
        ));
        VillagerPotentialEvents.onEntityTickPre(new EntityTickEvent.Pre(curedVillager));

        assertSame(transferredState, VillagerPotentialAttachments.get(curedVillager));
        verify(curedVillager, times(0)).setData(any(Supplier.class), any());
    }

    @Test
    void convertingVillagersRemainIndependent() {
        VillagerPotentialState first = state(0.7);
        VillagerPotentialState second = state(1.6);

        VillagerPotentialState firstZombie = VillagerPotentialAttachments.copyVillagerIdentity(
                first,
                mock(ZombieVillager.class),
                mock(HolderLookup.Provider.class)
        );
        VillagerPotentialState secondZombie = VillagerPotentialAttachments.copyVillagerIdentity(
                second,
                mock(ZombieVillager.class),
                mock(HolderLookup.Provider.class)
        );

        assertSame(first, firstZombie);
        assertSame(second, secondZombie);
        assertNotEquals(firstZombie, secondZombie);
    }

    @Test
    void conversionHookOnlyMaterializesTheTwoIdentityConversions() {
        ZombieVillager zombie = carrier(ZombieVillager.class, null, uuid(4));

        VillagerPotentialEvents.onLivingConversionPre(pre(zombie, EntityType.DROWNED));

        verify(zombie, times(0)).getData(any(Supplier.class));
        verify(zombie, times(0)).setData(any(Supplier.class), any());
        assertNull(VillagerPotentialAttachments.copyVillagerIdentity(
                state(1.0),
                mock(Entity.class),
                mock(HolderLookup.Provider.class)
        ));
    }

    private static VillagerPotentialState state(double aptitude) {
        return new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                Map.of(FARMER, aptitude),
                Map.of(FARMER, new ProfessionCareerState(
                        240L,
                        0.75,
                        10L,
                        100L,
                        Optional.of(SpecializationId.GENERAL)
                )),
                Optional.of(FARMER),
                Map.of(FARMER, new ProfessionActivityState(1.4, 250L)),
                Map.of(FARMER, new TradePaletteState(
                        List.of(WHEAT),
                        Map.of(WHEAT, new TradeHistory(
                                2L,
                                OptionalLong.of(220L),
                                1L,
                                OptionalLong.of(230L)
                        ))
                )),
                Map.of(FARMER, Map.of(WHEAT, new MarketDemandState(1.5, 1L, 230L)))
        );
    }

    private static LivingConversionEvent.Pre pre(
            Entity source,
            EntityType<? extends net.minecraft.world.entity.LivingEntity> outcome
    ) {
        return new LivingConversionEvent.Pre(
                (net.minecraft.world.entity.LivingEntity) source,
                outcome,
                ignored -> {
                }
        );
    }

    private static UUID uuid(int suffix) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Entity> T carrier(
            Class<T> entityClass,
            VillagerPotentialState state,
            UUID id
    ) {
        return carrierWithStateReference(entityClass, new AtomicReference<>(state), id);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Entity> T carrierWithStateReference(
            Class<T> entityClass,
            AtomicReference<VillagerPotentialState> storedState,
            UUID id
    ) {
        T entity = mock(entityClass);
        ServerLevel level = mock(ServerLevel.class);

        when(level.getSeed()).thenReturn(WORLD_SEED);
        when(entity.level()).thenReturn(level);
        when(entity.getUUID()).thenReturn(id);
        when(entity.getData(any(Supplier.class))).thenAnswer(ignored -> {
            VillagerPotentialState existing = storedState.get();
            if (existing != null) {
                return existing;
            }

            VillagerPotentialState defaultState = VillagerPotentialState.createDefault();
            storedState.set(defaultState);
            return defaultState;
        });
        when(entity.setData(any(Supplier.class), any())).thenAnswer(invocation ->
                storedState.getAndSet(invocation.getArgument(1))
        );
        return entity;
    }
}
