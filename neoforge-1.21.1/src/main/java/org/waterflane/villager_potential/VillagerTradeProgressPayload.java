package org.waterflane.villager_potential;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client-bound snapshot used by the two profession bars in the trade screen. */
public record VillagerTradeProgressPayload(
        int entityId,
        int professionLevel,
        double skill,
        double levelStartSkill,
        double nextLevelSkill,
        double baseSkillPerMinute,
        double skillPerMinute,
        double aptitudeMultiplier,
        double activityMultiplier,
        double activityBaseline,
        double activityMaximum,
        double activityGainPerTrade
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
        buffer.writeVarInt(payload.professionLevel);
        buffer.writeDouble(payload.skill);
        buffer.writeDouble(payload.levelStartSkill);
        buffer.writeDouble(payload.nextLevelSkill);
        buffer.writeDouble(payload.baseSkillPerMinute);
        buffer.writeDouble(payload.skillPerMinute);
        buffer.writeDouble(payload.aptitudeMultiplier);
        buffer.writeDouble(payload.activityMultiplier);
        buffer.writeDouble(payload.activityBaseline);
        buffer.writeDouble(payload.activityMaximum);
        buffer.writeDouble(payload.activityGainPerTrade);
    }

    private static VillagerTradeProgressPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerTradeProgressPayload(
                buffer.readVarInt(),
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
        );
    }

    @Override
    public Type<VillagerTradeProgressPayload> type() {
        return TYPE;
    }
}
