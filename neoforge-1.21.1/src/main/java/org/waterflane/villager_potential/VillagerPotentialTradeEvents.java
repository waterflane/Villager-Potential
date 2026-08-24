package org.waterflane.villager_potential;

import net.minecraft.world.entity.npc.Villager;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.TradeCategoryId;
import org.waterflane.villager_potential.core.TradeKey;
import org.waterflane.villager_potential.core.TradePaletteRerollStrategy;
import org.waterflane.villager_potential.core.api.ListenerRegistration;
import org.waterflane.villager_potential.core.api.PotentialView;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Synchronous, server-thread trade integration hooks. */
public final class VillagerPotentialTradeEvents {
    private static final CopyOnWriteArrayList<CandidateWeightModifier> WEIGHT_MODIFIERS =
            new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Consumer<PaletteEntriesGenerated>> GENERATED =
            new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Consumer<TradeProcessing>> PROCESSING =
            new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Consumer<TradeCompleted>> COMPLETED =
            new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Consumer<DemandChanged>> DEMAND_CHANGED =
            new CopyOnWriteArrayList<>();

    private VillagerPotentialTradeEvents() {
    }

    /**
     * Registers a final candidate-weight modifier. Modifiers run in registration
     * order. NaN, infinity, and negative results are ignored.
     */
    public static ListenerRegistration onCandidateWeight(CandidateWeightModifier modifier) {
        Objects.requireNonNull(modifier, "modifier");
        WEIGHT_MODIFIERS.add(modifier);
        return () -> WEIGHT_MODIFIERS.remove(modifier);
    }

    public static ListenerRegistration onPaletteEntriesGenerated(
            Consumer<PaletteEntriesGenerated> listener
    ) {
        return register(GENERATED, listener);
    }

    public static ListenerRegistration onTradeProcessing(Consumer<TradeProcessing> listener) {
        return register(PROCESSING, listener);
    }

    public static ListenerRegistration onTradeCompleted(Consumer<TradeCompleted> listener) {
        return register(COMPLETED, listener);
    }

    public static ListenerRegistration onDemandChanged(Consumer<DemandChanged> listener) {
        return register(DEMAND_CHANGED, listener);
    }

    static boolean hasCandidateWeightModifiers() {
        return !WEIGHT_MODIFIERS.isEmpty();
    }

    static double modifyCandidateWeight(CandidateWeight context, double originalWeight) {
        Objects.requireNonNull(context, "context");
        if (!Double.isFinite(originalWeight) || originalWeight < 0.0) {
            throw new IllegalArgumentException("originalWeight must be finite and non-negative");
        }
        double weight = originalWeight;
        for (CandidateWeightModifier modifier : WEIGHT_MODIFIERS) {
            double candidate = modifier.modify(context, weight);
            if (Double.isFinite(candidate) && candidate >= 0.0) {
                weight = candidate;
            }
        }
        return weight;
    }

    static void emitPaletteEntriesGenerated(PaletteEntriesGenerated event) {
        emit(GENERATED, event);
    }

    static void emitTradeProcessing(TradeProcessing event) {
        emit(PROCESSING, event);
    }

    static void emitTradeCompleted(TradeCompleted event) {
        emit(COMPLETED, event);
    }

    static void emitDemandChanged(DemandChanged event) {
        emit(DEMAND_CHANGED, event);
    }

    private static <T> ListenerRegistration register(
            CopyOnWriteArrayList<Consumer<T>> listeners,
            Consumer<T> listener
    ) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private static <T> void emit(CopyOnWriteArrayList<Consumer<T>> listeners, T event) {
        Objects.requireNonNull(event, "event");
        listeners.forEach(listener -> listener.accept(event));
    }

    @FunctionalInterface
    public interface CandidateWeightModifier {
        double modify(CandidateWeight context, double currentWeight);
    }

    public enum ProcessingKind {
        INITIAL_OR_NEW_LEVEL_GENERATION,
        REROLL,
        PERSISTENT_RESTORATION
    }

    public record CandidateWeight(
            Villager villager,
            ProfessionId profession,
            int professionLevel,
            TradeKey trade,
            TradeCategoryId category,
            TradePaletteRerollStrategy rerollStrategy
    ) {
        public CandidateWeight {
            Objects.requireNonNull(villager, "villager");
            Objects.requireNonNull(profession, "profession");
            Objects.requireNonNull(trade, "trade");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(rerollStrategy, "rerollStrategy");
        }
    }

    public record PaletteEntriesGenerated(
            Villager villager,
            ProfessionId profession,
            List<TradeKey> newEntries,
            ProcessingKind kind
    ) {
        public PaletteEntriesGenerated {
            Objects.requireNonNull(villager, "villager");
            Objects.requireNonNull(profession, "profession");
            newEntries = List.copyOf(newEntries);
            Objects.requireNonNull(kind, "kind");
            if (kind == ProcessingKind.PERSISTENT_RESTORATION) {
                throw new IllegalArgumentException("Restoration does not generate palette entries");
            }
        }
    }

    public record TradeProcessing(
            Villager villager,
            ProfessionId profession,
            TradePaletteRerollStrategy rerollStrategy,
            ProcessingKind kind,
            List<TradeKey> resultingTrades
    ) {
        public TradeProcessing {
            Objects.requireNonNull(villager, "villager");
            Objects.requireNonNull(profession, "profession");
            Objects.requireNonNull(rerollStrategy, "rerollStrategy");
            Objects.requireNonNull(kind, "kind");
            resultingTrades = List.copyOf(resultingTrades);
        }
    }

    public record TradeCompleted(
            Villager villager,
            ProfessionId profession,
            TradeKey trade,
            PotentialView potential
    ) {
        public TradeCompleted {
            Objects.requireNonNull(villager, "villager");
            Objects.requireNonNull(profession, "profession");
            Objects.requireNonNull(trade, "trade");
            Objects.requireNonNull(potential, "potential");
        }
    }

    public record DemandChanged(
            Villager villager,
            ProfessionId profession,
            TradeKey trade,
            Optional<PotentialView.DemandInfo> previousDemand,
            PotentialView.DemandInfo demand,
            PotentialView potential
    ) {
        public DemandChanged {
            Objects.requireNonNull(villager, "villager");
            Objects.requireNonNull(profession, "profession");
            Objects.requireNonNull(trade, "trade");
            Objects.requireNonNull(previousDemand, "previousDemand");
            Objects.requireNonNull(demand, "demand");
            Objects.requireNonNull(potential, "potential");
        }
    }
}
