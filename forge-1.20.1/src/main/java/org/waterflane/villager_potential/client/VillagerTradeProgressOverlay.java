package org.waterflane.villager_potential.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.waterflane.villager_potential.VillagerTradeProgressClientState;
import org.waterflane.villager_potential.VillagerTradeProgressPayload;
import org.waterflane.villager_potential.core.SkillProgression;
import org.waterflane.villager_potential.core.TradeProgressSnapshot;

import java.util.List;
import java.util.Locale;

/** Draws profession skill and trade-activity bars over the vanilla merchant UI. */
public final class VillagerTradeProgressOverlay {
    private static final ResourceLocation VILLAGER_TEXTURE = new ResourceLocation(
            "textures/gui/container/villager2.png"
    );
    private static final int BAR_X = 136;
    private static final int ACTIVITY_BAR_Y = 16;
    private static final int SKILL_BAR_Y = 23;
    private static final int BAR_WIDTH = 102;
    private static final int BAR_HEIGHT = 5;
    private static final int BLUE_DARK = 0xFF1556A8;
    private static final int BLUE_LIGHT = 0xFF4FA3FF;

    private VillagerTradeProgressOverlay() {
    }

    public static void render(
            MerchantScreen screen,
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        VillagerTradeProgressPayload payload = VillagerTradeProgressClientState.latest()
                .orElse(null);
        if (payload == null) {
            return;
        }
        TradeProgressSnapshot progress = payload.progress();

        int x = screen.getGuiLeft() + BAR_X;
        int activityY = screen.getGuiTop() + ACTIVITY_BAR_Y;
        int skillY = screen.getGuiTop() + SKILL_BAR_Y;
        renderGreenBar(graphics, x, activityY, activityFraction(progress));
        renderBlueBar(graphics, x, skillY, skillFraction(progress));

        if (inside(mouseX, mouseY, x, skillY)) {
            renderTooltip(graphics, skillTooltip(progress), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, x, activityY)) {
            renderTooltip(graphics, activityTooltip(progress), mouseX, mouseY);
        }
    }

    public static void clear() {
        VillagerTradeProgressClientState.clear();
    }

    static double skillFraction(TradeProgressSnapshot progress) {
        return progress.skillFraction();
    }

    static double activityFraction(TradeProgressSnapshot progress) {
        return progress.activityFraction();
    }

    private static void renderGreenBar(
            GuiGraphics graphics,
            int x,
            int y,
            double fraction
    ) {
        graphics.blit(VILLAGER_TEXTURE, x, y, 0, 0.0F, 186.0F,
                BAR_WIDTH, BAR_HEIGHT, 512, 256);
        int width = Mth.floor(BAR_WIDTH * fraction);
        if (width > 0) {
            graphics.blit(VILLAGER_TEXTURE, x, y, 0, 0.0F, 191.0F,
                    width, BAR_HEIGHT, 512, 256);
        }
    }

    private static void renderBlueBar(
            GuiGraphics graphics,
            int x,
            int y,
            double fraction
    ) {
        graphics.blit(VILLAGER_TEXTURE, x, y, 0, 0.0F, 186.0F,
                BAR_WIDTH, BAR_HEIGHT, 512, 256);
        int width = Mth.floor((BAR_WIDTH - 2) * fraction);
        if (width > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + width, y + BAR_HEIGHT - 1, BLUE_DARK);
            graphics.fill(x + 1, y + 1, x + 1 + width, y + 2, BLUE_LIGHT);
        }
    }

    static List<Component> skillTooltip(TradeProgressSnapshot progress) {
        if (progress.professionLevel() >= 5) {
            return List.of(
                    Component.translatable("tooltip.villager_potential.skill.title")
                            .withStyle(ChatFormatting.BLUE),
                    Component.translatable("tooltip.villager_potential.skill.maximum")
                            .withStyle(ChatFormatting.GRAY)
            );
        }
        double required = Math.max(
                0.0,
                progress.nextLevelSkill() - progress.levelStartSkill()
        );
        double earned = Mth.clamp(
                progress.skill() - progress.levelStartSkill(),
                0.0,
                required
        );
        double minutesRemaining = minutesRemaining(progress);
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable("tooltip.villager_potential.skill.title")
                .withStyle(ChatFormatting.BLUE));
        lines.add(Component.translatable(
                "tooltip.villager_potential.skill.aptitude_multiplier",
                colored(multiplier(progress.aptitudeMultiplier()), ChatFormatting.AQUA)
        ));
        lines.add(Component.translatable(
                "tooltip.villager_potential.skill.level_multiplier",
                colored(
                        multiplier(SkillProgression.professionLevelRateMultiplier(
                                progress.professionLevel()
                        )),
                        ChatFormatting.AQUA
                )
        ));
        lines.add(Component.translatable(
                "tooltip.villager_potential.skill.rate",
                colored(number(progress.baseSkillPerMinute()), ChatFormatting.AQUA),
                colored(multiplier(progress.aptitudeMultiplier()), ChatFormatting.AQUA),
                colored(multiplier(progress.activityMultiplier()), ChatFormatting.GREEN),
                colored(
                        multiplier(SkillProgression.professionLevelRateMultiplier(
                                progress.professionLevel()
                        )),
                        ChatFormatting.AQUA
                ),
                colored(number(progress.skillPerMinute()), ChatFormatting.BLUE)
        ));
        lines.add(Component.translatable(
                "tooltip.villager_potential.skill.current",
                colored(number(earned), ChatFormatting.BLUE),
                colored(number(required), ChatFormatting.DARK_AQUA)
        ));
        lines.add(Double.isFinite(minutesRemaining)
                ? Component.translatable(
                        "tooltip.villager_potential.skill.time_remaining",
                        colored(number(minutesRemaining), ChatFormatting.YELLOW)
                )
                : Component.translatable("tooltip.villager_potential.skill.paused")
                        .withStyle(ChatFormatting.GRAY));
        return List.copyOf(lines);
    }

    static double minutesRemaining(TradeProgressSnapshot progress) {
        return progress.minutesRemaining();
    }

    static List<Component> activityTooltip(TradeProgressSnapshot progress) {
        double remaining = Math.max(
                0.0,
                progress.activityMaximum() - progress.activityMultiplier()
        );
        return List.of(
                Component.translatable("tooltip.villager_potential.activity.title")
                        .withStyle(ChatFormatting.GREEN),
                Component.translatable(
                        "tooltip.villager_potential.activity.current",
                        colored(
                                multiplier(progress.activityMultiplier()),
                                ChatFormatting.GREEN
                        ),
                        colored(
                                multiplier(progress.activityMaximum()),
                                ChatFormatting.DARK_GREEN
                        )
                ),
                Component.translatable(
                        "tooltip.villager_potential.activity.remaining",
                        colored(multiplier(remaining), ChatFormatting.YELLOW)
                ),
                Component.translatable(
                        "tooltip.villager_potential.activity.trade_gain",
                        colored(
                                multiplier(progress.activityGainPerTrade()),
                                ChatFormatting.AQUA
                        )
                )
        );
    }

    private static Component colored(String value, ChatFormatting color) {
        return Component.literal(value).withStyle(color);
    }

    private static void renderTooltip(
            GuiGraphics graphics,
            List<Component> lines,
            int mouseX,
            int mouseY
    ) {
        graphics.renderTooltip(
                Minecraft.getInstance().font,
                lines.stream().map(Component::getVisualOrderText).toList(),
                mouseX,
                mouseY
        );
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + BAR_WIDTH
                && mouseY >= y && mouseY < y + BAR_HEIGHT;
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String multiplier(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
