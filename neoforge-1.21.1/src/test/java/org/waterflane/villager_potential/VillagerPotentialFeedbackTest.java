package org.waterflane.villager_potential;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.AptitudeDisplayMode;
import org.waterflane.villager_potential.core.AptitudeTier;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.VillagerPotentialState;
import org.waterflane.villager_potential.core.VillagerPotentialConfig;
import org.waterflane.villager_potential.core.api.PotentialViews;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class VillagerPotentialFeedbackTest {
    @Test
    void disabledModeProducesNoPlayerFeedback() {
        assertTrue(VillagerPotentialFeedback.describe(
                AptitudeDisplayMode.DISABLED,
                1.9,
                VillagerPotentialConfig.DEFAULT.aptitude()
        ).isEmpty());
        assertEquals(AptitudeDisplayMode.DISABLED, ServerConfig.playerAptitudeDisplayMode());

        ServerPlayer player = mock(ServerPlayer.class);
        Villager villager = mock(Villager.class);
        VillagerPotentialFeedback.showCurrentProfession(player, villager);
        verifyNoInteractions(player, villager);
    }

    @Test
    void qualitativeModeDoesNotCarryTheExactAptitude() {
        var description = VillagerPotentialFeedback.describe(
                AptitudeDisplayMode.QUALITATIVE,
                1.6,
                VillagerPotentialConfig.DEFAULT.aptitude()
        ).orElseThrow();

        assertEquals(AptitudeTier.TALENTED, description.tier());
        assertTrue(description.exactValue().isEmpty());
        var contents = assertInstanceOf(
                TranslatableContents.class,
                VillagerPotentialFeedback.component(description).getContents()
        );
        assertEquals("message.villager_potential.potential.qualitative", contents.getKey());
        Component tier = assertInstanceOf(Component.class, contents.getArgs()[0]);
        assertEquals(
                "tier.villager_potential.aptitude.talented",
                assertInstanceOf(TranslatableContents.class, tier.getContents()).getKey()
        );
    }

    @Test
    void exactModeRequiresExplicitModeAndStillUsesLocalizedComponents() {
        var description = VillagerPotentialFeedback.describe(
                AptitudeDisplayMode.EXACT,
                1.23456,
                VillagerPotentialConfig.DEFAULT.aptitude()
        ).orElseThrow();

        assertFalse(description.exactValue().isEmpty());
        assertEquals(1.23456, description.exactValue().getAsDouble());
        var contents = assertInstanceOf(
                TranslatableContents.class,
                VillagerPotentialFeedback.component(description).getContents()
        );
        assertEquals("message.villager_potential.potential.exact", contents.getKey());
        assertEquals("1.235", contents.getArgs()[1]);
    }

    @Test
    void professionScopedFeedbackReadsOnlyTheSelectedCurrentProfession() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionId farmer = ProfessionId.parse("minecraft:farmer");
        var view = PotentialViews.snapshot(VillagerPotentialState.createDefault()
                .withAptitude(librarian, 0.5)
                .withAptitude(farmer, 2.0));

        var librarianFeedback = VillagerPotentialFeedback.describeCurrentProfession(
                view,
                librarian,
                AptitudeDisplayMode.QUALITATIVE,
                VillagerPotentialConfig.DEFAULT.aptitude()
        ).orElseThrow();

        assertEquals(AptitudeTier.POOR, librarianFeedback.tier());
        assertTrue(librarianFeedback.exactValue().isEmpty());
    }
}
