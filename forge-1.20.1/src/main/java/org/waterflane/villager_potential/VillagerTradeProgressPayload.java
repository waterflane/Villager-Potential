package org.waterflane.villager_potential;

import net.minecraft.network.FriendlyByteBuf;
import org.waterflane.villager_potential.core.TradeProgressSnapshot;

/** Client-bound snapshot used by the two profession bars in the trade screen. */
public record VillagerTradeProgressPayload(
        int entityId,
        TradeProgressSnapshot progress
) {
    public static void encode(
            VillagerTradeProgressPayload payload,
            FriendlyByteBuf buffer
    ) {
        buffer.writeVarInt(payload.entityId);
        TradeProgressSnapshot progress = payload.progress;
        buffer.writeVarInt(progress.professionLevel());
        buffer.writeDouble(progress.skill());
        buffer.writeDouble(progress.levelStartSkill());
        buffer.writeDouble(progress.nextLevelSkill());
        buffer.writeDouble(progress.baseSkillPerMinute());
        buffer.writeDouble(progress.skillPerMinute());
        buffer.writeDouble(progress.aptitudeMultiplier());
        buffer.writeDouble(progress.activityMultiplier());
        buffer.writeDouble(progress.activityBaseline());
        buffer.writeDouble(progress.activityMaximum());
        buffer.writeDouble(progress.activityGainPerTrade());
    }

    public static VillagerTradeProgressPayload decode(FriendlyByteBuf buffer) {
        return new VillagerTradeProgressPayload(
                buffer.readVarInt(),
                new TradeProgressSnapshot(
                        buffer.readVarInt(),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readDouble()
                )
        );
    }
}
