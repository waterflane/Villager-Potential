package org.waterflane.villager_potential.core.api;

import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.SkillProgression;
import org.waterflane.villager_potential.core.SkillProgressionConfig;
import org.waterflane.villager_potential.core.TradeKey;
import org.waterflane.villager_potential.core.VillagerPotentialConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Canonical plain-text rendering of one {@link PotentialView} for
 * administrative inspection.
 *
 * <p>The exact output is part of the mod's operator contract: command output,
 * logs, and third-party admin tooling must stay identical across loaders, so
 * the formatting lives in core and platform commands only embed it.</p>
 */
public final class InspectionFormat {
    private InspectionFormat() {
    }

    public static List<String> format(
            UUID villagerId,
            int vanillaProfessionLevel,
            PotentialView view
    ) {
        return format(
                villagerId,
                vanillaProfessionLevel,
                view,
                VillagerPotentialConfig.DEFAULT.skill()
        );
    }

    public static List<String> format(
            UUID villagerId,
            int vanillaProfessionLevel,
            PotentialView view,
            SkillProgressionConfig progressionConfig
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("Villager Potential inspection");
        lines.add("uuid=" + villagerId);
        lines.add("schema_version=" + view.schemaVersion());
        lines.add("active_profession=" + view.activeProfession()
                .map(ProfessionId::toString).orElse("none"));
        lines.add("vanilla_profession_level=" + vanillaProfessionLevel);
        lines.add("aptitudes=" + sortedEntries(view.aptitudes(), Object::toString));

        lines.add("careers=" + view.careers().size());
        view.careers().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ProfessionId::toString)))
                .forEach(entry -> {
                    PotentialView.CareerInfo career = entry.getValue();
                    var progression = SkillProgression.progressTowardNextLevel(
                            career.skill(),
                            progressionConfig
                    );
                    lines.add("career[" + entry.getKey() + "]="
                            + "profession_time=" + career.accumulatedProfessionTime()
                            + ",skill=" + career.skill()
                            + ",derived_level=" + progression.currentLevel()
                            + ",progress_to_next=" + progression.progressTowardNextLevel()
                            + ",next_level_skill=" + (progression.nextLevelSkill().isPresent()
                                    ? progression.nextLevelSkill().getAsDouble()
                                    : "none")
                            + ",first_assignment=" + career.firstAssignment()
                            + ",latest_assignment=" + career.latestAssignment()
                            + ",specialization=" + career.specialization()
                                    .map(Object::toString).orElse("none"));
                });

        lines.add("persistent_palettes=" + view.learnedTradePalettes().size());
        view.learnedTradePalettes().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ProfessionId::toString)))
                .forEach(entry -> lines.add("palette[" + entry.getKey() + "]="
                        + summarizeTrades(entry.getValue())));

        lines.add("trade_memory=" + summarizeMemory(view.tradeMemory()));
        lines.add("demand=" + summarizeDemand(view.demand()));
        return List.copyOf(lines);
    }

    private static String summarizeMemory(
            Map<ProfessionId, Map<TradeKey, PotentialView.TradeMemoryEntry>> memory
    ) {
        if (memory.isEmpty()) {
            return "{}";
        }
        List<String> summaries = new ArrayList<>();
        memory.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ProfessionId::toString)))
                .forEach(entry -> {
                    long seen = entry.getValue().values().stream()
                            .mapToLong(PotentialView.TradeMemoryEntry::timesSeen).sum();
                    long used = entry.getValue().values().stream()
                            .mapToLong(PotentialView.TradeMemoryEntry::timesUsed).sum();
                    summaries.add(entry.getKey() + "={entries=" + entry.getValue().size()
                            + ",seen=" + seen + ",used=" + used + "}");
                });
        return "{" + String.join(", ", summaries) + "}";
    }

    private static String summarizeDemand(
            Map<ProfessionId, Map<TradeKey, PotentialView.DemandInfo>> demand
    ) {
        if (demand.isEmpty()) {
            return "{}";
        }
        List<String> summaries = new ArrayList<>();
        demand.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ProfessionId::toString)))
                .forEach(entry -> {
                    long purchases = entry.getValue().values().stream()
                            .mapToLong(PotentialView.DemandInfo::timesPurchased).sum();
                    double totalScore = entry.getValue().values().stream()
                            .mapToDouble(PotentialView.DemandInfo::score).sum();
                    summaries.add(entry.getKey() + "={entries=" + entry.getValue().size()
                            + ",score=" + totalScore + ",purchases=" + purchases + "}");
                });
        return "{" + String.join(", ", summaries) + "}";
    }

    private static String summarizeTrades(List<TradeKey> trades) {
        if (trades.isEmpty()) {
            return "[]";
        }
        int displayed = Math.min(trades.size(), 12);
        String result = trades.subList(0, displayed).toString();
        return displayed == trades.size()
                ? result
                : result.substring(0, result.length() - 1)
                        + ", ... (" + (trades.size() - displayed) + " more)]";
    }

    private static <T> String sortedEntries(
            Map<ProfessionId, T> values,
            Function<T, String> formatter
    ) {
        if (values.isEmpty()) {
            return "{}";
        }
        List<String> entries = new ArrayList<>();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ProfessionId::toString)))
                .forEach(entry -> entries.add(entry.getKey() + "=" + formatter.apply(entry.getValue())));
        return "{" + String.join(", ", entries) + "}";
    }
}
