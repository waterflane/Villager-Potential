package org.waterflane.villager_potential;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.SpecializationId;
import org.waterflane.villager_potential.core.TradeKey;
import org.waterflane.villager_potential.core.api.PotentialView;
import org.waterflane.villager_potential.core.api.PotentialViews;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Supported NeoForge 1.21.1 integration facade.
 *
 * <p>Call live access and mutation methods on the logical server thread. The
 * returned {@link PotentialView} is an immutable snapshot and may be retained
 * or read from another thread. Access initializes missing Potential lazily
 * through Villager Potential's persistence service; attachment details are not
 * part of this API.</p>
 */
public final class VillagerPotentialApi {
    private VillagerPotentialApi() {
    }

    public static PotentialView view(Villager villager) {
        return PotentialViews.snapshot(VillagerPotentialAttachments.get(requireVillager(villager)));
    }

    public static ProfessionId professionId(VillagerProfession profession) {
        return VillagerProfessionIds.fromMinecraft(profession);
    }

    public static OptionalDouble aptitude(Villager villager, VillagerProfession profession) {
        return view(villager).aptitude(professionId(profession));
    }

    public static OptionalDouble skill(Villager villager, VillagerProfession profession) {
        return view(villager).skill(professionId(profession));
    }

    public static Optional<PotentialView.CareerInfo> career(
            Villager villager,
            VillagerProfession profession
    ) {
        return view(villager).career(professionId(profession));
    }

    public static Optional<SpecializationId> specialization(
            Villager villager,
            VillagerProfession profession
    ) {
        return view(villager).specialization(professionId(profession));
    }

    public static List<TradeKey> learnedTradePalette(
            Villager villager,
            VillagerProfession profession
    ) {
        return view(villager).learnedTradePalette(professionId(profession));
    }

    public static Map<TradeKey, PotentialView.TradeMemoryEntry> tradeMemory(
            Villager villager,
            VillagerProfession profession
    ) {
        return view(villager).tradeMemory(professionId(profession));
    }

    public static Optional<PotentialView.DemandInfo> demand(
            Villager villager,
            VillagerProfession profession,
            TradeKey trade
    ) {
        return view(villager).demand(professionId(profession), trade);
    }

    /**
     * Explicitly assigns a datapack-supported specialization to an existing
     * career. It is idempotent for the same value and cannot replace an already
     * assigned specialization.
     */
    public static PotentialView assignSpecialization(
            Villager villager,
            VillagerProfession profession,
            SpecializationId specialization
    ) {
        return assignSpecialization(villager, professionId(profession), specialization);
    }

    public static PotentialView assignSpecialization(
            Villager villager,
            ProfessionId profession,
            SpecializationId specialization
    ) {
        return PotentialViews.snapshot(VillagerPotentialAttachments.assignApiSpecialization(
                requireVillager(villager),
                Objects.requireNonNull(profession, "profession"),
                Objects.requireNonNull(specialization, "specialization")
        ));
    }

    public static PotentialView setAptitude(
            Villager villager,
            ProfessionId profession,
            double aptitude
    ) {
        return PotentialViews.snapshot(VillagerPotentialAttachments.adminSetAptitude(
                requireVillager(villager),
                Objects.requireNonNull(profession, "profession"),
                aptitude
        ));
    }

    public static PotentialView setSkill(
            Villager villager,
            ProfessionId profession,
            double skill
    ) {
        return PotentialViews.snapshot(VillagerPotentialAttachments.adminSetSkill(
                requireVillager(villager),
                Objects.requireNonNull(profession, "profession"),
                skill
        ));
    }

    public static PotentialView resetProfession(
            Villager villager,
            ProfessionId profession
    ) {
        return PotentialViews.snapshot(VillagerPotentialAttachments.adminResetProfession(
                requireVillager(villager),
                Objects.requireNonNull(profession, "profession")
        ));
    }

    public static PotentialView regenerateProfession(
            Villager villager,
            ProfessionId profession
    ) {
        return PotentialViews.snapshot(VillagerPotentialAttachments.adminRegenerateProfession(
                requireVillager(villager),
                Objects.requireNonNull(profession, "profession")
        ));
    }

    public static PotentialView regenerateAll(Villager villager) {
        return PotentialViews.snapshot(VillagerPotentialAttachments.adminRegenerateAll(
                requireVillager(villager)
        ));
    }

    private static Villager requireVillager(Villager villager) {
        return Objects.requireNonNull(villager, "villager");
    }
}
