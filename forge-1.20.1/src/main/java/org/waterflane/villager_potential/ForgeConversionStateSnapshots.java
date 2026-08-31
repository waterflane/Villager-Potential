package org.waterflane.villager_potential;

import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

/** Retains conversion state before Forge invalidates the source capability. */
final class ForgeConversionStateSnapshots<S> {
    private final Map<S, VillagerPotentialState> states =
            Collections.synchronizedMap(new WeakHashMap<>());

    void remember(S source, VillagerPotentialState state) {
        states.put(
                Objects.requireNonNull(source, "source"),
                Objects.requireNonNull(state, "state")
        );
    }

    Optional<VillagerPotentialState> take(S source) {
        return Optional.ofNullable(states.remove(Objects.requireNonNull(source, "source")));
    }
}
