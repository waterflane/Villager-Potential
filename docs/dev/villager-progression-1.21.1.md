# Villager profession progression in Minecraft 1.21.1 / NeoForge 21.1

This note is limited to the profession, trade-completion, XP, level-up, offer-generation, and workstation/restock paths needed to replace vanilla profession progression later. It was verified against this workspace's generated `neoforge-21.1.248-sources.jar` (Minecraft 1.21.1, NeoForge 21.1.248).

## Pipeline at a glance

Vanilla's server-side progression path is:

```text
MerchantResultSlot#onTake
  -> MerchantOffer#take succeeds
  -> AbstractVillager#notifyTrade
       -> MerchantOffer#increaseUses
       -> Villager#rewardTradeXp
            -> villagerXp += MerchantOffer#getXp
            -> Villager#shouldIncreaseLevel
            -> schedule a 40-tick delayed update
  -> after trading has stopped, Villager#customServerAiStep counts down
  -> Villager#increaseMerchantCareer
       -> VillagerData#setLevel(current + 1)
       -> Villager#updateTrades
            -> select two listings for the new level
            -> append their MerchantOffers to the existing offers
```

XP is cumulative across the entire career. Level-up does not subtract XP or reset it to the new level's lower bound.

## Vanilla state and persistence

### Profession and level

`net.minecraft.world.entity.npc.VillagerData` is the immutable value containing:

- `VillagerType type`
- `VillagerProfession profession`
- `int level`

The level constants are `MIN_VILLAGER_LEVEL = 1` and `MAX_VILLAGER_LEVEL = 5`. The constructor only applies `Math.max(1, level)`; it does not clamp values above 5. Vanilla's own level-up guard is what stops ordinary progression at 5.

For a live `Villager`, the value is held in `Villager.DATA_VILLAGER_DATA`, an `EntityDataAccessor<VillagerData>` registered with `EntityDataSerializers.VILLAGER_DATA`. `Villager#defineSynchedData` initializes it to plains / `VillagerProfession.NONE` / level 1. `Villager#getVillagerData` reads it and `Villager#setVillagerData` replaces it. The latter clears the cached `offers` only when the profession changes; changing only the level does not clear offers.

`VillagerData.CODEC` persists the value under the entity NBT compound `VillagerData`, including the field named `level`. `Villager#addAdditionalSaveData` writes it. `Villager#readAdditionalSaveData` decodes it directly into `DATA_VILLAGER_DATA`.

### Villager XP

Profession XP is the private primitive field `Villager.villagerXp`. It is not part of `VillagerData` and is not synced entity data. The relevant accessors are `Villager#getVillagerXp` and `Villager#setVillagerXp`.

`Villager#addAdditionalSaveData` writes it as the integer NBT key `Xp`; `Villager#readAdditionalSaveData` restores that key when present. Trading screens receive the current value through `Merchant#openTradingScreen` and `Villager#resendOffersToTradingPlayer` as part of the merchant-offers packet.

Zombification and curing preserve both pieces separately: `Zombie#killedEntity` copies `VillagerData`, offers, and villager XP to the `ZombieVillager`; `ZombieVillager#finishConversion` restores all three to the cured `Villager`.

### Offers and per-offer XP

`AbstractVillager.offers` is the cached `MerchantOffers` list. Its entries are `MerchantOffer` objects. Each offer stores independent `uses`, `maxUses`, `demand`, and final `xp` fields. `MerchantOffer#getXp` returns the amount added to the villager for one successful use of that offer. The amount is chosen when a `VillagerTrades.ItemListing` creates the offer; it is not a single global "XP per trade" constant.

`AbstractVillager#addAdditionalSaveData` persists non-empty offers under `Offers`, and `AbstractVillager#readAdditionalSaveData` restores them. Consequently, changing the global trade-list registry does not rewrite offers already saved on villagers.

## XP thresholds

`VillagerData.NEXT_LEVEL_XP_THRESHOLDS` is the private array `{0, 10, 70, 150, 250}`. `VillagerData#getMaxXpPerLevel(level)` indexes the next entry, while `getMinXpPerLevel(level)` indexes the current level's entry. Both return 0 when `VillagerData#canLevelUp(level)` is false. `canLevelUp` accepts levels 1 through 4.

| Current level | Minimum cumulative XP for this level | Cumulative XP required for next level |
| ---: | ---: | ---: |
| 1 | 0 | 10 |
| 2 | 10 | 70 |
| 3 | 70 | 150 |
| 4 | 150 | 250 |
| 5 | not used by the guard | no next level |

The trading UI uses the same methods in `MerchantScreen#renderProgressBar`. `MerchantContainer#updateSellItem` exposes the selected offer's `getXp()` as `futureXp`, so the highlighted preview is also offer XP rather than a fixed trade count.

## What awards villager XP

The only ordinary vanilla gameplay action in the inspected paths that increments `Villager.villagerXp` is successfully completing a trade.

1. `MerchantResultSlot#onTake` obtains `MerchantContainer#getActiveOffer` and calls `MerchantOffer#take` on the two payment stacks (in either order).
2. Only when `take` succeeds does it call `Merchant#notifyTrade` and award the player `Stats.TRADED_WITH_VILLAGER`.
3. For a villager, `AbstractVillager#notifyTrade` increments the offer's use count with `MerchantOffer#increaseUses`, then calls the polymorphic `Villager#rewardTradeXp`.
4. `Villager#rewardTradeXp` adds exactly `offer.getXp()` to `villagerXp`, records the current trading player for the later reputation event, and evaluates level-up eligibility immediately.

`MerchantResultSlot#onTake` also calls `Merchant#overrideXp(current + offerXp)`. This is not a second server-side award: `AbstractVillager#overrideXp` is an empty implementation, inherited by `Villager`. The method updates `ClientSideMerchant.xp` on the client-side menu representation.

The `ExperienceOrb` optionally spawned by `Villager#rewardTradeXp` is player XP and is separate from `villagerXp`. Its value is random 3-6, plus 5 when that trade schedules a villager level-up, and its spawning is gated by `MerchantOffer#shouldRewardExp`. The offer's `rewardExp` flag does **not** gate the villager's `offer.getXp()` award.

Workstation use, profession assignment, farming/composting, resting, and restocking do not add villager XP.

## Evaluation and delayed level-up

`Villager#shouldIncreaseLevel` reads the current `VillagerData.level` and returns:

```java
VillagerData.canLevelUp(level)
    && villagerXp >= VillagerData.getMaxXpPerLevel(level)
```

`Villager#rewardTradeXp` calls this immediately after adding the completed offer's XP. If true, it sets the private `updateMerchantTimer` to 40 and `increaseProfessionLevelOnUpdate` to true. This is scheduling, not the level change itself.

`Villager#customServerAiStep` performs the delayed update. It decrements `updateMerchantTimer` only while `!isTrading()`. The timer therefore remains paused for as long as a trading player is attached. On the tick it reaches zero, the method calls `increaseMerchantCareer` if the flag is set, clears the flag, and grants the villager regeneration for 200 ticks.

`Villager#increaseMerchantCareer` increments the level by exactly one and calls `updateTrades`. It does not loop, recheck the threshold, or reduce XP. If externally supplied XP is high enough to cross multiple thresholds, vanilla still advances only one level for that scheduled update; another completed trade is needed to run `shouldIncreaseLevel` again.

The timer and pending flag are not written to NBT. Saving/unloading during the pending delay does not persist the scheduled level-up, although the already-awarded cumulative XP is saved and the next completed trade can schedule it again.

## Adding trades after level-up

`Villager#updateTrades` performs all vanilla profession-level offer selection:

1. Read the current profession and level from `VillagerData`.
2. If `FeatureFlags.TRADE_REBALANCE` is enabled, prefer that profession's `VillagerTrades.EXPERIMENTAL_TRADES` map and fall back to `VillagerTrades.TRADES`; otherwise use `TRADES`.
3. Read the `ItemListing[]` under the current numeric level.
4. Obtain the existing cached offers through `getOffers`.
5. Call `AbstractVillager#addOffersFromItemListings(..., 2)`.

`Villager.TRADES_PER_LEVEL` is 2. `addOffersFromItemListings` copies the listing array, randomly removes listings without replacement, calls each listing's `getOffer`, skips null results, and appends until two non-null offers have been added or the candidates are exhausted. Existing lower-level offers and their uses/demand are retained.

Offer generation is lazy on a new or profession-changed villager: `AbstractVillager#getOffers` creates an empty list and calls `updateTrades` when `offers == null`. A level change by itself does not trigger this lazy branch because `Villager#setVillagerData` only nulls offers for a profession change. Any replacement progression that sets the level outside `increaseMerchantCareer` must therefore also invoke the offer-addition path exactly once.

## Workstation, profession, trading, and restocking

### Acquiring and losing a profession

The adult brain installs `VillagerGoalPackages#getCorePackage` for profession/POI maintenance and installs `Activity.WORK` from `getWorkPackage(currentProfession, 0.5F)` only when `MemoryModuleType.JOB_SITE` is present.

`AssignProfessionFromJobSite#create` moves `POTENTIAL_JOB_SITE` to `JOB_SITE`. If the villager's profession is `NONE`, it finds the profession whose `heldJobSite` predicate accepts the POI, calls `Villager#setVillagerData(old.setProfession(...))`, and calls `Villager#refreshBrain`. The first subsequent `getOffers` builds level-1 offers for that profession.

`ResetProfession#create` can revert a villager with no `JOB_SITE` to `NONE` only when all of the following are true:

- profession is neither `NONE` nor `NITWIT`;
- `getVillagerXp() == 0`;
- `VillagerData.level <= 1`.

Thus vanilla XP has a second semantic role: once nonzero, it helps lock the profession even before a level-up. Replacing progression XP with a new value must deliberately preserve or replace this condition.

`PoiCompetitorScan#selectWinner` is another hidden XP consumer. When villagers claim the same matching job site, the villager with greater `getVillagerXp()` keeps the `JOB_SITE` memory; on a tie the reduction's second villager wins. The loser has `JOB_SITE` erased. A future XP suppression strategy changes this tie-breaking unless this behavior is also given a replacement comparison.

`Villager#setVillagerData` does not reset XP or level when the profession changes, and it clears offers but not either progression value. Normal `ResetProfession` only permits an untraded level-1 villager, which masks that fact in ordinary play; commands or other code can expose it.

### Work and restock path

`VillagerGoalPackages#getWorkPackage` uses `WorkAtComposter` for farmers and `WorkAtPoi` for other professions. `WorkAtPoi` requires `JOB_SITE`. For 300 ticks after `lastCheck` it cannot start; once the cooldown expires it retries a 50% random gate each tick until that gate passes, then updates `lastCheck` and requires the villager to be in the same dimension and within 1.73 blocks of the remembered POI.

When it starts, `WorkAtPoi#start`:

1. records `MemoryModuleType.LAST_WORKED_AT_POI`;
2. looks at the job site and plays the profession's work sound;
3. calls `useWorkstation` (empty in `WorkAtPoi`; farmer inventory/composter work in `WorkAtComposter#useWorkstation`);
4. calls `Villager#restock` only if `Villager#shouldRestock` returns true.

Restocking is offer availability/economy state, not progression:

- `MerchantOffer#needsRestock` is true when `uses > 0`.
- `Villager#allowedToRestock` allows the first restock of the day and a second only when fewer than two have occurred and more than 2,400 game ticks have elapsed since the last restock.
- `Villager#shouldRestock` also performs the 12,000-game-tick/day-boundary reset bookkeeping before checking allowance and whether any offer has uses. That reset calls `resetNumberOfRestocks`, whose `catchUpDemand` clears uses when a restock allowance remained, applies `updateDemand` once per missed allowance, and resends open-menu offers before setting the daily count to zero. It can therefore make `shouldRestock` return false because the catch-up step has already made the offers available.
- `Villager#restock` first calls `MerchantOffer#updateDemand` for every offer, then resets every offer's uses, resends offers to an open trading player, records `lastRestockGameTime`, and increments `numberOfRestocksToday`.
- `lastRestockGameTime` and `numberOfRestocksToday` persist as `LastRestock` and `RestocksToday`. `lastRestockCheckDayTime` is runtime-only.

Trading does not directly require a current `JOB_SITE`; it requires the villager to have non-empty offers. Losing a workstation prevents the normal `WorkAtPoi` restock path but does not remove offers from a traded/locked villager. Conversely, `WorkAtPoi` has no `isTrading()` guard. If it runs while a menu is open, `restock` explicitly calls `resendOffersToTradingPlayer`. This is separate from level-up timing, whose 40-tick countdown is explicitly paused while trading.

## NeoForge hooks versus vanilla interception points

### Existing NeoForge hooks

`TradeWithVillagerEvent`

- Posted by `AbstractVillager#notifyTrade` on the main NeoForge event bus, logical server only.
- Exposes the player, `MerchantOffer`, and `AbstractVillager`.
- Is non-cancellable and has no result.
- Is posted **after** `Villager#rewardTradeXp`, the threshold check/scheduling, and the trade criterion trigger.
- It is a good minimal signal for "trade activity modifies progression speed," but it cannot prevent the just-awarded vanilla XP or clear the already-private pending level-up state.

`VillagerTradesEvent`

- Posted by `VillagerTradingManager` during server-data tag reload, once per registered profession.
- Exposes mutable level-to-`ItemListing` lists which are written back to `VillagerTrades.TRADES`.
- Can add or replace listings and thereby control the XP of offers generated from those listings.
- It does not rewrite already-generated/saved `MerchantOffer` instances. `MerchantOffer.xp` is final and has no setter.
- The manager rewrites `VillagerTrades.TRADES`, not `EXPERIMENTAL_TRADES`; the trade-rebalance feature can therefore bypass these modified standard listings when an experimental map exists.

`EntityTickEvent.Pre` / `EntityTickEvent.Post`

- Fire for every ticking entity on both logical sides; handlers must filter to server-side `Villager` instances.
- Provide an event-only place to accumulate loaded profession time or compare a saved timestamp, without modifying `Villager#tick`.
- `Pre` is cancellable, but cancelling a villager's whole tick is not an appropriate progression interception. `Post` is the safer accumulation/evaluation signal.

NeoForge data attachments

- A serializable `AttachmentType` on the entity can hold profession identity, profession-start time, skill progress, and trade-derived rate modifiers without reusing vanilla `villagerXp`.
- Conversion persistence requirements and the exact attachment lifecycle are documented separately in `docs/dev/villager-lifecycle-1.21.1.md`.

There is no NeoForge 21.1.248 event specifically before villager XP award, on villager level-up, or on profession change. There is also no event that asks vanilla to append the newly unlocked level's offers.

### Smallest future interception surface

To replace `trade uses -> offer XP -> cumulative XP threshold -> level` with `profession time -> skill progression -> level`, while retaining trades as a speed input, the smallest robust surface is:

1. **Persistent skill state:** one serializable entity attachment. Store the active profession (or a profession epoch), elapsed/anchored time, skill progress, and any trade-derived rate modifier. Do not make `villagerXp` the authoritative new clock because `ResetProfession`, `PoiCompetitorScan`, merchant networking, and the vanilla progress bar already attach other semantics to it.
2. **Time accumulation and evaluation:** a filtered, logical-server `EntityTickEvent.Post` handler (or an equivalent lower-frequency scheduler). It should detect profession changes because NeoForge has no profession-change event, accumulate only the intended definition of "time in profession," and decide when a skill threshold is crossed.
3. **Trade activity input:** `TradeWithVillagerEvent`. It is correctly timed to record a completed successful trade and the exact offer, even though it is too late to suppress vanilla XP.
4. **Disable the vanilla progression branch at its source:** a narrowly targeted interception inside `Villager#rewardTradeXp`, specifically the `villagerXp += offer.getXp()` / `shouldIncreaseLevel()` scheduling portion. Cancelling the whole method would also remove the normal trade reputation handoff and player XP-orb behavior. Setting XP back in `TradeWithVillagerEvent` is insufficient because the private pending timer/flag may already have been set.
5. **Reuse vanilla level application and offer append:** expose/invoke `Villager#increaseMerchantCareer` as one atomic operation, or reproduce its exact pair `setVillagerData(old.setLevel(oldLevel + 1))` then `updateTrades`. Merely setting `VillagerData.level` will not append new offers. Reusing `increaseMerchantCareer` preserves the one-level increment and the normal two-offer generation path.
6. **Choose the two old-XP side effects explicitly:** replace or preserve `ResetProfession`'s `XP == 0` lock and `PoiCompetitorScan`'s XP comparison. These are outside the numeric level threshold but change behavior if vanilla XP is held at zero.

The core vanilla interception points are therefore only `Villager#rewardTradeXp` (stop XP-driven scheduling without losing other trade effects) and `Villager#increaseMerchantCareer`/`updateTrades` (apply a skill-driven level and append offers). Time tracking and trade-rate input can use NeoForge events and attachment storage. No workstation or restock method needs interception for the progression replacement unless "time in profession" is intentionally defined as active workstation time rather than elapsed employed time.

## Verification inventory

The implementation statements above were checked against these mapped 1.21.1 sources:

- `net.minecraft.world.entity.npc.Villager`: state, NBT, trade reward, threshold scheduling, delayed level-up, offer generation, restock, trading packets.
- `net.minecraft.world.entity.npc.VillagerData`: profession/level storage and threshold constants/methods.
- `net.minecraft.world.entity.npc.AbstractVillager`: offer caching/persistence and the completed-trade dispatch.
- `net.minecraft.world.item.trading.MerchantOffer`: offer XP, uses, payment consumption, demand, and restock state.
- `net.minecraft.world.item.trading.Merchant`: initial merchant packet.
- `net.minecraft.world.inventory.MerchantResultSlot` and `MerchantContainer`: successful payment boundary and future-XP preview.
- `net.minecraft.client.gui.screens.inventory.MerchantScreen`: vanilla threshold/progress rendering.
- `net.minecraft.world.entity.ai.behavior.VillagerGoalPackages`, `AssignProfessionFromJobSite`, `ResetProfession`, `PoiCompetitorScan`, `WorkAtPoi`, and `WorkAtComposter`: profession/workstation state and restocking.
- `net.minecraft.world.entity.monster.Zombie` and `ZombieVillager`: conversion copying of data/offers/XP.
- `net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent`, `event.village.VillagerTradesEvent`, `common.VillagerTradingManager`, and `event.tick.EntityTickEvent`: available NeoForge hooks and their timing/limits.
