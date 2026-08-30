package org.waterflane.villager_potential;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.ProfessionCareerState;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.SpecializationId;
import org.waterflane.villager_potential.core.TradeKey;
import org.waterflane.villager_potential.core.TradePaletteRerollStrategy;
import org.waterflane.villager_potential.core.VillagerPotentialState;
import org.waterflane.villager_potential.core.api.PotentialViews;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VillagerPotentialCommandsTest {
    @Test
    void inspectionRequiresAdministratorAndMutationsRequireHighestPermission() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        VillagerPotentialCommands.register(dispatcher);
        var root = dispatcher.getRoot().getChild("villagerpotential");
        CommandSourceStack user = mock(CommandSourceStack.class);
        CommandSourceStack administrator = mock(CommandSourceStack.class);
        CommandSourceStack owner = mock(CommandSourceStack.class);
        when(user.hasPermission(2)).thenReturn(false);
        when(administrator.hasPermission(2)).thenReturn(true);
        when(administrator.hasPermission(4)).thenReturn(false);
        when(owner.hasPermission(2)).thenReturn(true);
        when(owner.hasPermission(4)).thenReturn(true);

        assertNotNull(root);
        assertFalse(root.canUse(user));
        assertTrue(root.canUse(administrator));
        assertNotNull(root.getChild("reload"));
        assertNotNull(root.getChild("inspect"));
        assertFalse(root.getChild("set").canUse(administrator));
        assertFalse(root.getChild("reset").canUse(administrator));
        assertFalse(root.getChild("regenerate").canUse(administrator));
        assertTrue(root.getChild("set").canUse(owner));
        assertTrue(root.getChild("reset").canUse(owner));
        assertTrue(root.getChild("regenerate").canUse(owner));
    }

    @Test
    void destructiveOperationsHaveOnlyExplicitSubcommands() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        VillagerPotentialCommands.register(dispatcher);
        var root = dispatcher.getRoot().getChild("villagerpotential");
        var regenerate = root.getChild("regenerate");

        assertNull(regenerate.getCommand());
        assertNotNull(regenerate.getChild("profession"));
        assertNotNull(regenerate.getChild("all"));
        assertNull(root.getChild("reset").getChild("all"));
        assertNull(root.getChild("regenerateall"));
    }

    @Test
    void inspectFormatsInitializedStateIncludingPersistentPaletteAndSummaries() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        SpecializationId archivist = SpecializationId.parse("example:archivist");
        TradeKey paper = new TradeKey.Offer(
                new TradeKey.Item("minecraft:paper", 24),
                new TradeKey.Item("minecraft:emerald", 1)
        );
        VillagerPotentialState state = VillagerPotentialState.createDefault()
                .withAptitude(librarian, 1.25)
                .assignProfession(librarian, 100L)
                .withCareer(
                        librarian,
                        new ProfessionCareerState(4_000L, 0.75, 100L, 300L)
                                .withSpecialization(archivist)
                )
                .recordPresentedTrades(
                        librarian,
                        List.of(paper),
                        List.of(paper),
                        500L,
                        16,
                        TradePaletteRerollStrategy.PERSISTENT
                )
                .recordTradeUse(librarian, paper, 600L, 16)
                .recordTradePurchase(librarian, paper, 600L);
        UUID villagerId = UUID.fromString("00000000-0000-0000-0000-000000000123");

        String inspection = String.join("\n", VillagerPotentialCommands.formatInspection(
                villagerId,
                3,
                PotentialViews.snapshot(state)
        ));

        assertTrue(inspection.contains("uuid=" + villagerId));
        assertTrue(inspection.contains("schema_version=" + state.schemaVersion()));
        assertTrue(inspection.contains("aptitudes={minecraft:librarian=1.25}"));
        assertTrue(inspection.contains("profession_time=4000,skill=0.75"));
        assertTrue(inspection.contains("vanilla_profession_level=3"));
        assertTrue(inspection.contains("specialization=example:archivist"));
        assertTrue(inspection.contains("palette[minecraft:librarian]=["));
        assertTrue(inspection.contains("trade_memory={minecraft:librarian={entries=1,seen=1,used=1}}"));
        assertTrue(inspection.contains("demand={minecraft:librarian={entries=1,score="));
        assertTrue(inspection.contains("purchases=1}"));
    }
}
