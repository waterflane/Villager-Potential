package org.waterflane.villager_potential;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.waterflane.villager_potential.core.AptitudeDisplayMode;
import org.waterflane.villager_potential.core.AptitudeGenerationConfig;
import org.waterflane.villager_potential.core.AptitudeTier;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.api.PotentialView;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** Builds a small server-authored action-bar hint without adding a custom screen. */
final class VillagerPotentialFeedback {
    private VillagerPotentialFeedback() {
    }

    static void showCurrentProfession(ServerPlayer player, Villager villager) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(villager, "villager");
        AptitudeDisplayMode mode = ServerConfig.playerAptitudeDisplayMode();
        if (!mode.visible()) {
            return;
        }

        VillagerProfession minecraftProfession = villager.getVillagerData().getProfession();
        if (minecraftProfession == VillagerProfession.NONE
                || minecraftProfession == VillagerProfession.NITWIT) {
            return;
        }
        Optional<ProfessionId> profession =
                VillagerProfessionIds.tryFromMinecraft(minecraftProfession);
        if (profession.isEmpty()) {
            return;
        }

        describeCurrentProfession(
                VillagerPotentialApi.view(villager),
                profession.orElseThrow(),
                mode,
                ServerConfig.gameplayConfig().aptitude()
        ).map(VillagerPotentialFeedback::component)
                .ifPresent(message -> player.displayClientMessage(message, true));
    }

    static Optional<Description> describeCurrentProfession(
            PotentialView view,
            ProfessionId profession,
            AptitudeDisplayMode mode,
            AptitudeGenerationConfig config
    ) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(profession, "profession");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(config, "config");
        if (!mode.visible()) {
            return Optional.empty();
        }
        OptionalDouble aptitude = view.aptitude(profession);
        return aptitude.isPresent()
                ? describe(mode, aptitude.getAsDouble(), config)
                : Optional.empty();
    }

    static Optional<Description> describe(
            AptitudeDisplayMode mode,
            double aptitude,
            AptitudeGenerationConfig config
    ) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(config, "config");
        if (!mode.visible()) {
            return Optional.empty();
        }
        AptitudeTier tier = AptitudeTier.classify(aptitude, config);
        return Optional.of(new Description(
                tier,
                mode.exactValueVisible()
                        ? OptionalDouble.of(aptitude)
                        : OptionalDouble.empty()
        ));
    }

    static Component component(Description description) {
        Objects.requireNonNull(description, "description");
        Component tier = Component.translatable(tierKey(description.tier()));
        if (description.exactValue().isEmpty()) {
            return Component.translatable(
                    "message.villager_potential.potential.qualitative",
                    tier
            );
        }
        DecimalFormat format = new DecimalFormat(
                "0.###",
                DecimalFormatSymbols.getInstance(Locale.ROOT)
        );
        return Component.translatable(
                "message.villager_potential.potential.exact",
                tier,
                format.format(description.exactValue().getAsDouble())
        );
    }

    private static String tierKey(AptitudeTier tier) {
        return switch (tier) {
            case POOR -> "tier.villager_potential.aptitude.poor";
            case AVERAGE -> "tier.villager_potential.aptitude.average";
            case PROMISING -> "tier.villager_potential.aptitude.promising";
            case TALENTED -> "tier.villager_potential.aptitude.talented";
            case EXCEPTIONAL -> "tier.villager_potential.aptitude.exceptional";
        };
    }

    record Description(AptitudeTier tier, OptionalDouble exactValue) {
        Description {
            Objects.requireNonNull(tier, "tier");
            Objects.requireNonNull(exactValue, "exactValue");
        }
    }
}
