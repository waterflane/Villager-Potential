package org.waterflane.villager_potential;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.SpecializationId;
import org.waterflane.villager_potential.core.api.ListenerRegistration;
import org.waterflane.villager_potential.core.api.PotentialView;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Semantic lifecycle hooks fired synchronously on the logical server thread.
 * Skill events are batched with persisted progression and are not fired per tick.
 */
public final class VillagerPotentialLifecycleEvents {
    private static final CopyOnWriteArrayList<Consumer<Initialized>> INITIALIZED =
            new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Consumer<Inherited>> INHERITED =
            new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Consumer<ProfessionChanged>> PROFESSION_CHANGED =
            new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Consumer<SkillChanged>> SKILL_CHANGED =
            new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Consumer<VanillaLevelChanged>> LEVEL_CHANGED =
            new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Consumer<SpecializationAssigned>> SPECIALIZATION =
            new CopyOnWriteArrayList<>();

    private VillagerPotentialLifecycleEvents() {
    }

    public static ListenerRegistration onInitialized(Consumer<Initialized> listener) {
        return register(INITIALIZED, listener);
    }

    public static ListenerRegistration onInherited(Consumer<Inherited> listener) {
        return register(INHERITED, listener);
    }

    public static ListenerRegistration onProfessionChanged(Consumer<ProfessionChanged> listener) {
        return register(PROFESSION_CHANGED, listener);
    }

    public static ListenerRegistration onSkillChanged(Consumer<SkillChanged> listener) {
        return register(SKILL_CHANGED, listener);
    }

    public static ListenerRegistration onVanillaLevelChanged(
            Consumer<VanillaLevelChanged> listener
    ) {
        return register(LEVEL_CHANGED, listener);
    }

    public static ListenerRegistration onSpecializationAssigned(
            Consumer<SpecializationAssigned> listener
    ) {
        return register(SPECIALIZATION, listener);
    }

    static void emitInitialized(Initialized event) {
        emit(INITIALIZED, event);
    }

    static void emitInherited(Inherited event) {
        emit(INHERITED, event);
    }

    static void emitProfessionChanged(ProfessionChanged event) {
        emit(PROFESSION_CHANGED, event);
    }

    static void emitSkillChanged(SkillChanged event) {
        emit(SKILL_CHANGED, event);
    }

    static void emitVanillaLevelChanged(VanillaLevelChanged event) {
        emit(LEVEL_CHANGED, event);
    }

    static void emitSpecializationAssigned(SpecializationAssigned event) {
        emit(SPECIALIZATION, event);
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

    public record Initialized(Entity entity, PotentialView potential) {
        public Initialized {
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(potential, "potential");
        }
    }

    public record Inherited(
            Villager child,
            Villager firstParent,
            Villager secondParent,
            PotentialView potential
    ) {
        public Inherited {
            Objects.requireNonNull(child, "child");
            Objects.requireNonNull(firstParent, "firstParent");
            Objects.requireNonNull(secondParent, "secondParent");
            Objects.requireNonNull(potential, "potential");
        }
    }

    public record ProfessionChanged(
            Villager villager,
            Optional<ProfessionId> previousProfession,
            Optional<ProfessionId> profession,
            PotentialView potential
    ) {
        public ProfessionChanged {
            Objects.requireNonNull(villager, "villager");
            Objects.requireNonNull(previousProfession, "previousProfession");
            Objects.requireNonNull(profession, "profession");
            Objects.requireNonNull(potential, "potential");
        }
    }

    public record SkillChanged(
            Villager villager,
            ProfessionId profession,
            double previousSkill,
            double skill,
            PotentialView potential
    ) {
        public SkillChanged {
            Objects.requireNonNull(villager, "villager");
            Objects.requireNonNull(profession, "profession");
            Objects.requireNonNull(potential, "potential");
        }
    }

    public record VanillaLevelChanged(
            Villager villager,
            ProfessionId profession,
            int previousLevel,
            int level,
            PotentialView potential
    ) {
        public VanillaLevelChanged {
            Objects.requireNonNull(villager, "villager");
            Objects.requireNonNull(profession, "profession");
            Objects.requireNonNull(potential, "potential");
        }
    }

    public record SpecializationAssigned(
            Villager villager,
            ProfessionId profession,
            SpecializationId specialization,
            PotentialView potential
    ) {
        public SpecializationAssigned {
            Objects.requireNonNull(villager, "villager");
            Objects.requireNonNull(profession, "profession");
            Objects.requireNonNull(specialization, "specialization");
            Objects.requireNonNull(potential, "potential");
        }
    }
}
