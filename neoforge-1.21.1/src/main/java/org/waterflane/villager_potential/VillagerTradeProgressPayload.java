package org.waterflane.villager_potential;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.waterflane.villager_potential.core.TradeProgressSnapshot;

/** Client-bound snapshot used by the two profession bars in the trade screen. */
public record VillagerTradeProgressPayload(
        int entityId,
        TradeProgressSnapshot progress
) implements CustomPacketPayload {
    public static final Type<VillagerTradeProgressPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Villager_potential.MODID, "trade_progress")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerTradeProgressPayload>
            STREAM_CODEC = StreamCodec.of(
                    VillagerTradeProgressPayload::encode,
                    VillagerTradeProgressPayload::decode
            );

    private static void encode(
            RegistryFriendlyByteBuf buffer,
            VillagerTradeProgressPayload payload
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

    private static VillagerTradeProgressPayload decode(RegistryFriendlyByteBuf buffer) {
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

    @Override
    public Type<VillagerTradeProgressPayload> type() {
        return TYPE;
    }
}
