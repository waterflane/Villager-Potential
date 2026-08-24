package org.waterflane.villager_potential.core.api;

import org.waterflane.villager_potential.core.MarketDemandState;
import org.waterflane.villager_potential.core.ProfessionCareerState;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.TradeHistory;
import org.waterflane.villager_potential.core.TradeKey;
import org.waterflane.villager_potential.core.TradePaletteState;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** Creates supported API snapshots without exposing persistence containers. */
public final class PotentialViews {
    private PotentialViews() {
    }

    public static PotentialView snapshot(VillagerPotentialState state) {
        return new Snapshot(Objects.requireNonNull(state, "state"));
    }

    private static final class Snapshot implements PotentialView {
        private final Map<ProfessionId, Double> aptitudes;
        private final int schemaVersion;
        private final Map<ProfessionId, CareerInfo> careers;
        private final Optional<ProfessionId> activeProfession;
        private final Map<ProfessionId, List<TradeKey>> palettes;
        private final Map<ProfessionId, Map<TradeKey, TradeMemoryEntry>> memory;
        private final Map<ProfessionId, Map<TradeKey, DemandInfo>> demand;

        private Snapshot(VillagerPotentialState state) {
            schemaVersion = state.schemaVersion();
            aptitudes = Map.copyOf(state.aptitudes());
            careers = mapCareers(state.careers());
            activeProfession = state.activeProfession();
            palettes = mapPalettes(state.tradePalettes());
            memory = mapMemory(state.tradePalettes());
            demand = mapDemand(state.marketDemand());
        }

        @Override
        public int schemaVersion() {
            return schemaVersion;
        }

        @Override
        public Map<ProfessionId, Double> aptitudes() {
            return aptitudes;
        }

        @Override
        public OptionalDouble aptitude(ProfessionId profession) {
            Double value = aptitudes.get(Objects.requireNonNull(profession, "profession"));
            return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
        }

        @Override
        public Map<ProfessionId, CareerInfo> careers() {
            return careers;
        }

        @Override
        public Optional<CareerInfo> career(ProfessionId profession) {
            return Optional.ofNullable(careers.get(Objects.requireNonNull(profession, "profession")));
        }

        @Override
        public Optional<ProfessionId> activeProfession() {
            return activeProfession;
        }

        @Override
        public OptionalDouble skill(ProfessionId profession) {
            CareerInfo career = careers.get(Objects.requireNonNull(profession, "profession"));
            return career == null ? OptionalDouble.empty() : OptionalDouble.of(career.skill());
        }

        @Override
        public Optional<org.waterflane.villager_potential.core.SpecializationId> specialization(
                ProfessionId profession
        ) {
            return career(profession).flatMap(CareerInfo::specialization);
        }

        @Override
        public Map<ProfessionId, List<TradeKey>> learnedTradePalettes() {
            return palettes;
        }

        @Override
        public List<TradeKey> learnedTradePalette(ProfessionId profession) {
            return palettes.getOrDefault(
                    Objects.requireNonNull(profession, "profession"),
                    List.of()
            );
        }

        @Override
        public Map<ProfessionId, Map<TradeKey, TradeMemoryEntry>> tradeMemory() {
            return memory;
        }

        @Override
        public Map<TradeKey, TradeMemoryEntry> tradeMemory(ProfessionId profession) {
            return memory.getOrDefault(
                    Objects.requireNonNull(profession, "profession"),
                    Map.of()
            );
        }

        @Override
        public Map<ProfessionId, Map<TradeKey, DemandInfo>> demand() {
            return demand;
        }

        @Override
        public Map<TradeKey, DemandInfo> demand(ProfessionId profession) {
            return demand.getOrDefault(
                    Objects.requireNonNull(profession, "profession"),
                    Map.of()
            );
        }

        @Override
        public Optional<DemandInfo> demand(ProfessionId profession, TradeKey trade) {
            Objects.requireNonNull(trade, "trade");
            return Optional.ofNullable(demand(profession).get(trade));
        }

        private static Map<ProfessionId, CareerInfo> mapCareers(
                Map<ProfessionId, ProfessionCareerState> source
        ) {
            Map<ProfessionId, CareerInfo> result = new HashMap<>();
            source.forEach((profession, career) -> result.put(profession, new CareerInfo(
                    career.accumulatedProfessionTime(),
                    career.learnedSkill(),
                    career.firstAssignment(),
                    career.latestAssignment(),
                    career.specialization()
            )));
            return Map.copyOf(result);
        }

        private static Map<ProfessionId, List<TradeKey>> mapPalettes(
                Map<ProfessionId, TradePaletteState> source
        ) {
            Map<ProfessionId, List<TradeKey>> result = new HashMap<>();
            source.forEach((profession, palette) -> result.put(
                    profession,
                    List.copyOf(palette.activeTrades())
            ));
            return Map.copyOf(result);
        }

        private static Map<ProfessionId, Map<TradeKey, TradeMemoryEntry>> mapMemory(
                Map<ProfessionId, TradePaletteState> source
        ) {
            Map<ProfessionId, Map<TradeKey, TradeMemoryEntry>> result = new HashMap<>();
            source.forEach((profession, palette) -> {
                Map<TradeKey, TradeMemoryEntry> professionMemory = new HashMap<>();
                palette.offerHistory().forEach((trade, history) -> professionMemory.put(
                        trade,
                        memoryEntry(history)
                ));
                result.put(profession, Map.copyOf(professionMemory));
            });
            return Map.copyOf(result);
        }

        private static TradeMemoryEntry memoryEntry(TradeHistory history) {
            return new TradeMemoryEntry(
                    history.timesSeen(),
                    history.lastSeen(),
                    history.timesUsed(),
                    history.lastUsed()
            );
        }

        private static Map<ProfessionId, Map<TradeKey, DemandInfo>> mapDemand(
                Map<ProfessionId, Map<TradeKey, MarketDemandState>> source
        ) {
            Map<ProfessionId, Map<TradeKey, DemandInfo>> result = new HashMap<>();
            source.forEach((profession, entries) -> {
                Map<TradeKey, DemandInfo> professionDemand = new HashMap<>();
                entries.forEach((trade, value) -> professionDemand.put(trade, new DemandInfo(
                        value.demandScore(),
                        value.timesPurchased(),
                        value.lastPurchaseGameTime()
                )));
                result.put(profession, Map.copyOf(professionDemand));
            });
            return Map.copyOf(result);
        }
    }
}
