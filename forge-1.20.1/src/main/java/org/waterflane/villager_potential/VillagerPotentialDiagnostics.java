package org.waterflane.villager_potential;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.SpecializationId;
import org.waterflane.villager_potential.core.TradeKey;
import org.waterflane.villager_potential.core.TradePaletteRerollStrategy;

import java.util.Objects;
import java.util.UUID;

/** Concise opt-in diagnostics; never logs per-tick progression or full state. */
final class VillagerPotentialDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();

    private VillagerPotentialDiagnostics() {
    }

    static void initialization(UUID villager, int schemaVersion, int aptitudeCount) {
        writeInitialization(
                LOGGER,
                ServerConfig.diagnosticLoggingEnabled(),
                villager,
                schemaVersion,
                aptitudeCount
        );
    }

    static void writeInitialization(
            Logger logger,
            boolean enabled,
            UUID villager,
            int schemaVersion,
            int aptitudeCount
    ) {
        if (enabled) {
            write(logger, true, "initialized villager=" + villager + " schema=" + schemaVersion
                    + " aptitudes=" + aptitudeCount);
        }
    }

    static void migration(int previousSchema, int schema) {
        if (enabled()) {
            write(LOGGER, true, "migrated Potential schema=" + previousSchema + "->" + schema);
        }
    }

    static void inheritance(UUID child, UUID firstParent, UUID secondParent) {
        if (enabled()) {
            write(LOGGER, true, "inherited villager=" + child + " parents=" + firstParent + ","
                    + secondParent);
        }
    }

    static void profession(
            UUID villager,
            java.util.Optional<ProfessionId> previous,
            java.util.Optional<ProfessionId> profession
    ) {
        if (enabled()) {
            write(LOGGER, true, "profession villager=" + villager + " "
                    + previous.map(Object::toString).orElse("none") + "->"
                    + profession.map(Object::toString).orElse("none"));
        }
    }

    static void specialization(
            UUID villager,
            ProfessionId profession,
            SpecializationId specialization
    ) {
        if (enabled()) {
            write(LOGGER, true, "specialization villager=" + villager + " profession="
                    + profession + " value=" + specialization);
        }
    }

    static void learned(UUID villager, ProfessionId profession, int count) {
        if (enabled()) {
            write(LOGGER, true, "learned trades villager=" + villager + " profession="
                    + profession + " new=" + count);
        }
    }

    static void tradeDecision(
            UUID villager,
            ProfessionId profession,
            TradePaletteRerollStrategy strategy,
            String decision
    ) {
        if (enabled()) {
            write(LOGGER, true, "trade decision villager=" + villager + " profession="
                    + profession + " mode=" + strategy + " decision=" + decision);
        }
    }

    static double weight(
            UUID villager,
            ProfessionId profession,
            TradeKey trade,
            double weight
    ) {
        if (ServerConfig.detailedWeightLoggingEnabled()) {
            LOGGER.info("[Villager Potential/debug] weight villager={} profession={} trade={} resolved={}",
                    villager, profession, trade, weight);
        }
        return weight;
    }

    static void demand(
            UUID villager,
            ProfessionId profession,
            TradeKey trade,
            double previous,
            double current
    ) {
        if (enabled()) {
            write(LOGGER, true, "demand villager=" + villager + " profession=" + profession
                    + " trade=" + trade + " score=" + previous + "->" + current);
        }
    }

    static void price(
            UUID villager,
            ProfessionId profession,
            TradeKey trade,
            double demand,
            int vanillaPrice,
            int adjustedPrice
    ) {
        if (enabled()) {
            write(LOGGER, true, "price villager=" + villager + " profession=" + profession
                    + " trade=" + trade + " demand=" + demand + " value=" + vanillaPrice
                    + "->" + adjustedPrice);
        }
    }

    static void persistence(String operation, String message) {
        LOGGER.warn("Villager Potential persistence {} failed: {}", operation, message);
    }

    private static boolean enabled() {
        return ServerConfig.diagnosticLoggingEnabled();
    }

    static void write(Logger logger, boolean enabled, String message) {
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(message, "message");
        if (enabled) {
            logger.info("[Villager Potential/debug] " + message);
        }
    }
}
