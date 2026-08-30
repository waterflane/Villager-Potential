# Villager Potential integration API

The supported API is split between a platform-neutral read model and equivalent
Forge 1.20.1 / NeoForge 1.21.1 facades. Integrations should not use
`VillagerPotentialAttachments`; capability, attachment, codec, and persistence
containers are implementation details.

Consume the API by declaring a normal mod dependency on `villager_potential`
and calling the public classes below. There is no service-loader registration;
the facade and the `VillagerPotentialService` SPI (currently
`API_VERSION = 2`) are obtained directly, as shown in this document.

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

A snapshot exposes: `schemaVersion()`, aptitudes per profession, careers as
`CareerInfo(accumulatedProfessionTime, skill, firstAssignment,
latestAssignment, specialization)`, `activeProfession()`, per-profession
`skill` and `specialization`, learned palettes as portable `TradeKey` lists,
aggregate `TradeMemoryEntry(timesSeen, lastSeen, timesUsed, lastUsed)` history,
and stored `DemandInfo(score, timesPurchased, lastPurchaseGameTime)` per trade.
Reading `DemandInfo` never applies time decay.

Calling either platform facade can lazily initialize missing Potential and must be
done on the logical server thread. A returned snapshot can safely be retained
and read later. Query the facade again when current state is required.

Supported explicit mutations are specialization assignment and the narrowly
scoped administrative operations to set one aptitude, set one existing
career's skill, reset one profession's derived state, regenerate one
profession, or explicitly regenerate all Potential. These operations validate
inputs and always pass through the persistence/event service; they never expose
or accept loader persistence containers. Specialization assignment additionally checks
the active datapack definition and cannot replace an existing different value.
The administrative operations are intended for trusted server tooling, not
ordinary gameplay integrations.

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
returned if selected. Candidate-weight modification is the only mutating hook;
every other trade event is a read-only notification carrying immutable
collections and snapshots.

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
`core.api.VillagerPotentialService` SPI and obtain its platform implementation
from `VillagerPotentialServices.forServer(server)`. It looks up loaded villagers
by UUID, returns immutable views, and exposes only supported explicit mutations.
A bridge running in a genuinely compatible plugin or hybrid environment may
delegate to this service. Stock Forge/NeoForge is not claimed to be Paper-compatible;
this project contains no Bukkit or Paper API or compatibility shim.

## Administration and diagnostics

All platform commands use the `/villagerpotential` root. `inspect <villager>`
and `reload` require permission level 2. Mutations require permission level 4:

- `set aptitude <villager> <profession-id> <value>`;
- `set skill <villager> <profession-id> <value>`;
- `reset profession <villager> <profession-id>`;
- `regenerate profession <villager> <profession-id>`;
- `regenerate all <villager>`.

`reset profession` preserves that profession's aptitude and every unrelated
profession. Regeneration is never implicit: the destructive paths exist only
under the explicit `regenerate profession` and `regenerate all` literals.
Commands delegate to the public versioned service rather than accessing
capabilities or attachments.

The server config's `[debug] enabled` option activates concise lifecycle,
trade-processing, persistent restoration/learning, and demand/price messages.
`detailedTradeWeights` additionally logs resolved candidate weights and only
takes effect while debug logging is enabled. Both options default to false;
per-tick progression and full-state dumps are never logged.
