# Villager Potential integration API

The supported API is split between a platform-neutral read model and a
NeoForge 1.21.1 facade. Integrations should not use
`VillagerPotentialAttachments`; attachment types, codecs, and persistence
containers are implementation details.

## Reading Potential

Use `org.waterflane.villager_potential.VillagerPotentialApi`:

```java
PotentialView potential = VillagerPotentialApi.view(villager);
ProfessionId librarian = VillagerPotentialApi.professionId(
        VillagerProfession.LIBRARIAN
);

potential.aptitude(librarian);
potential.skill(librarian);
potential.career(librarian);
potential.specialization(librarian);
potential.learnedTradePalette(librarian);
potential.tradeMemory(librarian);
potential.demand(librarian);
```

`ProfessionId`, `SpecializationId`, and `TradeKey` are portable core values;
they do not retain registry holders or Minecraft offer objects. `PotentialView`
is a point-in-time immutable snapshot. Its lists and maps are unmodifiable and
are not persistence containers.

Calling the NeoForge facade can lazily initialize missing Potential and must be
done on the logical server thread. A returned snapshot can safely be retained
and read later. Query the facade again when current state is required.

The only supported external mutation is
`VillagerPotentialApi.assignSpecialization`. It requires an existing career,
checks the active datapack definition, is idempotent for the current value, and
cannot replace a career's specialization. This prevents integrations from
installing an invalid state or bypassing persistence and schema handling.

## Lifecycle hooks

Register listeners with
`org.waterflane.villager_potential.VillagerPotentialLifecycleEvents`. Hooks cover:

- lazy Potential initialization;
- inherited child Potential creation;
- profession changes, including clearing a profession;
- batched skill changes;
- applied vanilla profession-level changes;
- first specialization assignment.

Hooks run synchronously on the logical server thread. Skill change hooks are
emitted when a progression batch is persisted (normally every 20 eligible
ticks), not for every tiny per-tick increment. Close the returned
`ListenerRegistration` when the listener is no longer needed.

## Trade hooks

`org.waterflane.villager_potential.VillagerPotentialTradeEvents` exposes final
candidate-weight modification,
new persistent learned-palette entries, reroll processing, successful completed
trades, and stored demand changes.

Candidate modifiers run in registration order. Each receives the current final
weight and returns a replacement. Negative, NaN, and infinite listener values
are ignored. With no modifier installed, the resolver takes the same selection
and RNG paths as before. When a modifier is installed, candidates are
materialized once so the hook receives the portable `TradeKey` that will be
returned if selected.

`TradeProcessing.kind()` distinguishes:

- `INITIAL_OR_NEW_LEVEL_GENERATION` for genuinely generated offers;
- `REROLL` for processing by a non-persistent reroll mode;
- `PERSISTENT_RESTORATION` when learned persistent offers are reconstructed.

Persistent restoration never fires `PaletteEntriesGenerated`. The new-entry
event contains only portable `TradeKey` values newly learned in that operation.
Event collections and Potential snapshots are immutable.

All hooks are synchronous and server-thread-only. Listeners should finish
quickly and must not retain or mutate Minecraft offer collections. The API has
no Bukkit or Paper dependency.

## External content and future bridges

Registered modded professions use their registry name as a portable
`ProfessionId`. Aptitude is generated deterministically when a villager first
enters a profession that was not known during initial creation. Career time,
skill, the `villager_potential:general` fallback specialization, and persistence
then use the same core state as built-in professions.

Foreign trade listings default to the `general` category. Structured offers
made from registered items and persistable components receive durable
`TradeKey` values and can participate in palette memory, demand, and persistent
restoration. If that identity cannot be established, the offer receives a
preserve-only fallback key: it remains available through its original trade
system but does not enter memory or demand. If every learned persistent offer
cannot be restored, Villager Potential yields to the originating trade system
instead of cancelling it or returning an incomplete palette.

For a future companion bridge, use the versioned
`core.api.VillagerPotentialService` SPI and obtain its NeoForge implementation
from `VillagerPotentialServices.forServer(server)`. It looks up loaded villagers
by UUID, returns immutable views, and exposes only supported explicit mutations.
A bridge running in a genuinely compatible plugin or hybrid environment may
delegate to this service. Stock NeoForge is not claimed to be Paper-compatible;
this project contains no Bukkit or Paper API or compatibility shim.
