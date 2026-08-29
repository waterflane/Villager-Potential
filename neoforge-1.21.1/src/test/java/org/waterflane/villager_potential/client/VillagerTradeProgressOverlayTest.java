package org.waterflane.villager_potential.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.VillagerTradeProgressPayload;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class VillagerTradeProgressOverlayTest {
    @Test
    void skillBarTracksOnlyTheCurrentLevelInterval() {
        VillagerTradeProgressPayload payload = payload(0.75, 0.5, 1.0, 1.0);

        assertEquals(0.5, VillagerTradeProgressOverlay.skillFraction(payload));
    }

    @Test
    void activityBarTracksTheConfiguredBaselineToMaximumRange() {
        VillagerTradeProgressPayload payload = payload(0.75, 0.5, 1.0, 1.4);

        assertEquals(0.4, VillagerTradeProgressOverlay.activityFraction(payload), 0.000_000_1);
    }

    @Test
    void bothBarsClampOvershoot() {
        VillagerTradeProgressPayload payload = payload(1.5, 0.5, 1.0, 2.5);

        assertEquals(1.0, VillagerTradeProgressOverlay.skillFraction(payload));
        assertEquals(1.0, VillagerTradeProgressOverlay.activityFraction(payload));
    }

    @Test
    void skillBarIsEmptyAfterReachingMaster() {
        VillagerTradeProgressPayload payload = new VillagerTradeProgressPayload(
                7,
                5,
                10.5,
                10.5,
                10.5,
                0.0,
                1.4,
                1.0,
                1.0,
                2.0,
                0.1
        );

        assertEquals(0.0, VillagerTradeProgressOverlay.skillFraction(payload));
    }

    @Test
    void remainingSkillIsConvertedToMinutesAtTheCurrentRate() {
        VillagerTradeProgressPayload payload = payload(0.75, 0.5, 1.0, 1.0);

        assertEquals(
                (1.0 - 0.75) / 0.075,
                VillagerTradeProgressOverlay.minutesRemaining(payload),
                0.000_000_1
        );
    }

    @Test
    void stoppedProgressHasNoFiniteCompletionEstimate() {
        VillagerTradeProgressPayload payload = new VillagerTradeProgressPayload(
                7, 3, 0.75, 0.5, 1.0, 0.0, 1.4, 1.0, 1.0, 2.0, 0.1
        );

        assertEquals(
                Double.POSITIVE_INFINITY,
                VillagerTradeProgressOverlay.minutesRemaining(payload)
        );
    }

    @Test
    void skillCoefficientsUseRestrainedBlueHighlights() {
        List<Component> tooltip = VillagerTradeProgressOverlay.skillTooltip(
                payload(0.75, 0.5, 1.0, 1.0)
        );

        assertColor(tooltip.get(0), ChatFormatting.BLUE);
        assertArgumentColor(tooltip.get(1), 0, ChatFormatting.AQUA);
        assertArgumentColor(tooltip.get(2), 0, ChatFormatting.AQUA);
        assertArgumentColor(tooltip.get(3), 0, ChatFormatting.AQUA);
        assertArgumentColor(tooltip.get(4), 0, ChatFormatting.BLUE);
        assertArgumentColor(tooltip.get(5), 0, ChatFormatting.YELLOW);
    }

    @Test
    void individualAptitudeIsDisplayedSeparatelyFromTheLevelBonus() {
        VillagerTradeProgressPayload payload = new VillagerTradeProgressPayload(
                7, 1, 0.75, 0.0, 1.5, 0.075, 1.37, 1.0, 1.0, 2.0, 0.1
        );

        List<Component> tooltip = VillagerTradeProgressOverlay.skillTooltip(payload);

        assertTranslationAndArgument(tooltip.get(1),
                "tooltip.villager_potential.skill.aptitude_multiplier", "1.37");
        assertTranslationAndArgument(tooltip.get(2),
                "tooltip.villager_potential.skill.level_multiplier", "1.00");
    }

    @Test
    void tradeCoefficientsUseRestrainedGreenHighlights() {
        List<Component> tooltip = VillagerTradeProgressOverlay.activityTooltip(
                payload(0.75, 0.5, 1.0, 1.4)
        );

        assertColor(tooltip.get(0), ChatFormatting.GREEN);
        assertArgumentColor(tooltip.get(1), 0, ChatFormatting.GREEN);
        assertArgumentColor(tooltip.get(1), 1, ChatFormatting.DARK_GREEN);
        assertArgumentColor(tooltip.get(2), 0, ChatFormatting.YELLOW);
        assertArgumentColor(tooltip.get(3), 0, ChatFormatting.AQUA);
    }

    private static void assertArgumentColor(
            Component line,
            int argumentIndex,
            ChatFormatting expected
    ) {
        TranslatableContents contents = assertInstanceOf(
                TranslatableContents.class,
                line.getContents()
        );
        Component argument = assertInstanceOf(
                Component.class,
                contents.getArgs()[argumentIndex]
        );
        assertColor(argument, expected);
    }

    private static void assertTranslationAndArgument(
            Component line,
            String expectedKey,
            String expectedArgument
    ) {
        TranslatableContents contents = assertInstanceOf(
                TranslatableContents.class,
                line.getContents()
        );
        assertEquals(expectedKey, contents.getKey());
        Component argument = assertInstanceOf(Component.class, contents.getArgs()[0]);
        assertEquals(expectedArgument, argument.getString());
    }

    private static void assertColor(Component component, ChatFormatting expected) {
        assertEquals(expected.getColor(), component.getStyle().getColor().getValue());
    }

    private static VillagerTradeProgressPayload payload(
            double skill,
            double levelStart,
            double nextLevel,
            double activity
    ) {
        return new VillagerTradeProgressPayload(
                7,
                3,
                skill,
                levelStart,
                nextLevel,
                0.075,
                1.37,
                activity,
                1.0,
                2.0,
                0.1
        );
    }
}
