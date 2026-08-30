package org.waterflane.villager_potential.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.TradeProgressSnapshot;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class VillagerTradeProgressOverlayTest {
    @Test
    void skillBarTracksOnlyTheCurrentLevelInterval() {
        TradeProgressSnapshot payload = payload(0.75, 0.5, 1.0, 1.0);

        assertEquals(0.5, VillagerTradeProgressOverlay.skillFraction(payload));
    }

    @Test
    void activityBarTracksTheConfiguredBaselineToMaximumRange() {
        TradeProgressSnapshot payload = payload(0.75, 0.5, 1.0, 1.4);

        assertEquals(0.4, VillagerTradeProgressOverlay.activityFraction(payload), 0.000_000_1);
    }

    @Test
    void bothBarsClampOvershoot() {
        TradeProgressSnapshot payload = payload(1.5, 0.5, 1.0, 2.5);

        assertEquals(1.0, VillagerTradeProgressOverlay.skillFraction(payload));
        assertEquals(1.0, VillagerTradeProgressOverlay.activityFraction(payload));
    }

    @Test
    void skillBarIsEmptyAfterReachingMaster() {
        TradeProgressSnapshot payload = new TradeProgressSnapshot(
                5,
                10.5,
                10.5,
                10.5,
                0.0,
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
        TradeProgressSnapshot payload = payload(0.75, 0.5, 1.0, 1.0);

        assertEquals(
                (1.0 - 0.75) / 0.075,
                VillagerTradeProgressOverlay.minutesRemaining(payload),
                0.000_000_1
        );
    }

    @Test
    void stoppedProgressHasNoFiniteCompletionEstimate() {
        TradeProgressSnapshot payload = new TradeProgressSnapshot(
                3, 0.75, 0.5, 1.0, 0.0, 0.0, 1.4, 1.0, 1.0, 2.0, 0.1
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
        assertArgumentColor(tooltip.get(3), 1, ChatFormatting.AQUA);
        assertArgumentColor(tooltip.get(3), 2, ChatFormatting.GREEN);
        assertArgumentColor(tooltip.get(3), 3, ChatFormatting.AQUA);
        assertArgumentColor(tooltip.get(3), 4, ChatFormatting.BLUE);
        assertArgumentColor(tooltip.get(4), 0, ChatFormatting.BLUE);
        assertArgumentColor(tooltip.get(5), 0, ChatFormatting.YELLOW);
    }

    @Test
    void individualAptitudeIsDisplayedSeparatelyFromTheLevelBonus() {
        TradeProgressSnapshot payload = new TradeProgressSnapshot(
                1, 0.75, 0.0, 1.5, 0.05, 0.0685, 1.37, 1.0, 1.0, 2.0, 0.1
        );

        List<Component> tooltip = VillagerTradeProgressOverlay.skillTooltip(payload);

        assertTranslationAndArgument(tooltip.get(1),
                "tooltip.villager_potential.skill.aptitude_multiplier", "1.37");
        assertTranslationAndArgument(tooltip.get(2),
                "tooltip.villager_potential.skill.level_multiplier", "1.00");
    }

    @Test
    void rateLineShowsBaseAndEveryMultiplierBeforeTheCurrentRate() {
        double currentRate = 0.05 * 1.37 * 1.25 * 1.44;
        TradeProgressSnapshot payload = new TradeProgressSnapshot(
                3, 0.75, 0.5, 1.0, 0.05, currentRate,
                1.37, 1.25, 1.0, 2.0, 0.1 / Math.pow(1.2, 2)
        );

        TranslatableContents rate = assertInstanceOf(
                TranslatableContents.class,
                VillagerTradeProgressOverlay.skillTooltip(payload).get(3).getContents()
        );

        assertEquals("tooltip.villager_potential.skill.rate", rate.getKey());
        assertEquals("0.050", ((Component) rate.getArgs()[0]).getString());
        assertEquals("1.37", ((Component) rate.getArgs()[1]).getString());
        assertEquals("1.25", ((Component) rate.getArgs()[2]).getString());
        assertEquals("1.44", ((Component) rate.getArgs()[3]).getString());
        assertEquals("0.123", ((Component) rate.getArgs()[4]).getString());
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

    private static TradeProgressSnapshot payload(
            double skill,
            double levelStart,
            double nextLevel,
            double activity
    ) {
        return new TradeProgressSnapshot(
                3,
                skill,
                levelStart,
                nextLevel,
                0.05,
                0.075,
                1.37,
                activity,
                1.0,
                2.0,
                0.1 / Math.pow(1.2, 2)
        );
    }
}
