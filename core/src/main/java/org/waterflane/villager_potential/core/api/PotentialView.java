package org.waterflane.villager_potential.core.api;

import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.SpecializationId;
import org.waterflane.villager_potential.core.TradeKey;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * Stable, read-only integration view of one villager's Potential.
 *
 * <p>Implementations are immutable snapshots. They can be retained by an
 * integration, but do not update when the persisted Potential changes.</p>
 */
public interface PotentialView {
    int schemaVersion();

    Map<ProfessionId, Double> aptitudes();

    OptionalDouble aptitude(ProfessionId profession);

    Map<ProfessionId, CareerInfo> careers();

    Optional<CareerInfo> career(ProfessionId profession);

    Optional<ProfessionId> activeProfession();

    OptionalDouble skill(ProfessionId profession);

    Optional<SpecializationId> specialization(ProfessionId profession);

    Map<ProfessionId, List<TradeKey>> learnedTradePalettes();

    List<TradeKey> learnedTradePalette(ProfessionId profession);

    Map<ProfessionId, Map<TradeKey, TradeMemoryEntry>> tradeMemory();

    Map<TradeKey, TradeMemoryEntry> tradeMemory(ProfessionId profession);

    Map<ProfessionId, Map<TradeKey, DemandInfo>> demand();

    Map<TradeKey, DemandInfo> demand(ProfessionId profession);

    Optional<DemandInfo> demand(ProfessionId profession, TradeKey trade);

    /** Career and tenure information for one portable profession id. */
    record CareerInfo(
            long accumulatedProfessionTime,
            double skill,
            long firstAssignment,
            long latestAssignment,
            Optional<SpecializationId> specialization
    ) {
        public CareerInfo {
            specialization = java.util.Objects.requireNonNull(
                    specialization,
                    "specialization"
            );
        }
    }

    /** Aggregate Trade Memory for one portable logical trade. */
    record TradeMemoryEntry(
            long timesSeen,
            OptionalLong lastSeen,
            long timesUsed,
            OptionalLong lastUsed
    ) {
    }

    /** Stored demand state. Reading this value does not apply time decay. */
    record DemandInfo(
            double score,
            long timesPurchased,
            long lastPurchaseGameTime
    ) {
    }
}
