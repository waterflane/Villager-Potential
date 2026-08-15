# Vanilla villager trade selection in Minecraft 1.21.1 / NeoForge 21.1

This note maps the standard villager trade-generation path, the vanilla candidate pools, the trade-rebalance experiment, and the directly adjacent NeoForge hooks. It was derived from this workspace's generated `neoforge-21.1.248-sources.jar` (Minecraft 1.21.1, NeoForge 21.1.248), rather than from wiki trade tables.

The scope is the 13 employed vanilla professions. `NONE` and `NITWIT` have no entries in `VillagerTrades.TRADES` and therefore generate no profession offers. Wandering-trader pools are separate and are out of scope.

## Conclusion for Villager Potential

Villager Potential can preserve and wrap the live vanilla `ItemListing` pools. It does **not** need a duplicated hardcoded table of vanilla trades.

- For the normal pools, `VillagerTradesEvent#getTrades()` exposes mutable level 1-5 lists after NeoForge has copied the original vanilla arrays. An event listener can replace each existing `ItemListing` with a wrapper that retains the original listing as its delegate.
- Vanilla's chooser has no weight API. Applying genuine specialization weights therefore also requires intercepting/replacing the uniform removal step in `AbstractVillager#addOffersFromItemListings`, or the villager-specific call site in `Villager#updateTrades`, with weighted sampling without replacement.
- Duplicating an array entry to imitate an integer weight is not equivalent: it permits the same logical listing to be chosen twice. Making a wrapper randomly return `null` is also not equivalent: it can reduce the number of offers and does not normalize probabilities over the remaining candidates.
- The trade-rebalance experiment is an exception to the NeoForge event route. `VillagerTradingManager` rebuilds and publishes only `VillagerTrades.TRADES`; it does not publish `VillagerTrades.EXPERIMENTAL_TRADES`. When `FeatureFlags.TRADE_REBALANCE` is active, librarians, armorers, and cartographers read their pool from `EXPERIMENTAL_TRADES` and bypass event changes to the corresponding normal pool. Supporting that feature without a copied table requires wrapping/interpreting the selected experimental array at `Villager#updateTrades`, or wrapping the public experimental maps separately.
- The delegates remain the source of truth for prices, outputs, enchantment tags/providers, map searches, villager-type behavior, and future NeoForge additions. A specialization policy still needs a way to classify a listing. Because vanilla listing configuration fields are generally private, classification by input/output may require Mixin accessors or metadata attached while wrapping; that is a small classification layer, not a duplicated trade table.

Existing generated `MerchantOffer`s are not pool entries. Wrapping a pool affects offers generated afterward; it does not rewrite offers already cached or saved on villagers.

## Selection algorithm and probability model

`Villager#updateTrades` chooses one source map:

1. With `TRADE_REBALANCE`, use `VillagerTrades.EXPERIMENTAL_TRADES[profession]` when that profession has an override; otherwise use `VillagerTrades.TRADES[profession]`.
2. Without the feature, use `VillagerTrades.TRADES[profession]`.
3. Read only the array at the villager's **current numeric level**.
4. Pass that array and a requested count of 2 to `AbstractVillager#addOffersFromItemListings`.

`addOffersFromItemListings` copies the array to a mutable list, removes one uniformly random list element, calls its `ItemListing#getOffer(entity, random)`, and increments the result count only when the returned offer is non-null. It stops after two non-null offers or when the copied list is empty.

Consequences:

- There are no explicit candidate weights. Every **array occurrence** has the same chance on each draw.
- Sampling is without replacement. A listing object occurring once cannot be chosen twice in one level-generation pass.
- When all `n` factories are non-null, the method adds `min(2, n)` offers. For `n >= 2`, every candidate has inclusion probability `2/n`, and every unordered pair has probability `1 / C(n, 2)`. For `n = 1`, that candidate is added with probability 1.
- A null factory is discarded but does not consume either of the two result slots. Given a fixed set of `s` factories that will succeed during the pass, the result is the first two successes in a uniformly random permutation: all successes are included if `s <= 2`, otherwise each successful candidate has conditional inclusion probability `2/s`.
- An unconditional probability cannot be stated when success itself depends on world state, registry/tag contents, or a data-driven factory. The relevant cases are called out below instead of assigning guessed percentages.
- Lower-level offers are not reselected on level-up. Each level adds its own pass to the existing `MerchantOffers`; previous offers, uses, and demand remain.

The level map is the only vanilla profession-level restriction on a listing. Factories do not re-check the villager's profession or level. Ordinary progression starts at level 1, can advance only from levels 1-4, and stops at level 5. A missing profession map, missing level key, or level outside the map adds nothing.

## Pool notation

The tables below describe the actual `ItemListing[]` entries. Each semicolon-separated alternative is one equal-probability array entry unless a `{16 colors}` or similar expansion says that it represents multiple separate entries.

- `buy X -> Y` means the villager takes `X` and gives `Y`.
- `sell X -> Y` is written from the player's perspective only to make emerald-priced output listings easy to scan; it still means the villager takes `X` and gives `Y`.
- `convert A + B -> C` is a two-input listing.
- `[uses/xp]` gives the configured maximum uses and villager XP per completed use.
- `n` is the physical source-array length. `add` is the number of non-null offers normally added. `p` is each candidate's inclusion probability when all candidates in that row succeed.

Dynamic price demand, reputation discounts, and special-price adjustment occur after the listing configures its base offer and do not affect candidate selection.

## Normal vanilla pools (`VillagerTrades.TRADES`)

### Farmer

| Level | Source pool | Selection |
| ---: | --- | --- |
| 1 | buy 20 wheat -> 1 emerald `[16/2]`; buy 26 potato -> 1 emerald `[16/2]`; buy 22 carrot -> 1 emerald `[16/2]`; buy 15 beetroot -> 1 emerald `[16/2]`; sell 1 emerald -> 6 bread `[16/1]` | `n=5`, add 2, `p=2/5` |
| 2 | buy 6 pumpkin -> 1 emerald `[12/10]`; sell 1 emerald -> 4 pumpkin pie `[12/5]`; sell 1 emerald -> 4 apple `[16/5]` | `n=3`, add 2, `p=2/3` |
| 3 | sell 3 emerald -> 18 cookie `[12/10]`; buy 4 melon -> 1 emerald `[12/20]` | `n=2`, add 2, `p=1` |
| 4 | sell 1 emerald -> 1 cake `[12/15]`; six separate sell-1-emerald suspicious-stew entries: night vision 100 ticks, jump boost 160, weakness 140, blindness 120, poison 280, saturation 7 `[12/15 each]` | `n=7`, add 2, `p=2/7` |
| 5 | sell 3 emerald -> 3 golden carrot `[12/30]`; sell 4 emerald -> 3 glistering melon slice `[12/30]` | `n=2`, add 2, `p=1` |

The six stews are fixed candidates. The factory does not randomly choose an effect after selection.

### Fisherman

| Level | Source pool | Selection |
| ---: | --- | --- |
| 1 | buy 20 string -> 1 emerald `[16/2]`; buy 10 coal -> 1 emerald `[16/2]`; convert 6 cod + 1 emerald -> 6 cooked cod `[16/1]`; sell 3 emerald -> 1 cod bucket `[16/1]` | `n=4`, add 2, `p=1/2` |
| 2 | buy 15 cod -> 1 emerald `[16/10]`; convert 6 salmon + 1 emerald -> 6 cooked salmon `[16/5]`; sell 2 emerald -> 1 campfire `[12/5]` | `n=3`, add 2, `p=2/3` |
| 3 | buy 13 salmon -> 1 emerald `[16/20]`; sell a randomly enchanted fishing rod, base emerald cost 3 `[3/10]` | `n=2`, add 2, `p=1` |
| 4 | buy 6 tropical fish -> 1 emerald `[12/30]` | `n=1`, add 1, `p=1` |
| 5 | buy 4 pufferfish -> 1 emerald `[12/30]`; buy 1 type-dependent boat -> 1 emerald `[12/30]` | `n=2`, add 2, `p=1` |

The boat input is oak for plains, spruce for taiga and snow, jungle for desert and jungle, acacia for savanna, and dark oak for swamp. All seven vanilla villager types are mapped, so this factory is non-null for vanilla types. NeoForge patches it to return null for an unmapped custom type.

### Shepherd

| Level | Source pool | Selection |
| ---: | --- | --- |
| 1 | four separate buys of 18 wool -> 1 emerald: white, brown, black, gray `[16/2]`; sell 2 emerald -> shears `[12/1]` | `n=5`, add 2, `p=2/5` |
| 2 | five separate buys of 12 dye -> 1 emerald: white, gray, black, light blue, lime `[16/10]`; sell 1 emerald -> 1 wool, one separate entry for each of all 16 colors `[16/5]`; sell 1 emerald -> 4 carpet, one separate entry for each of all 16 colors `[16/5]` | `n=37`, add 2, `p=2/37` |
| 3 | five separate buys of 12 dye -> 1 emerald: yellow, light gray, orange, red, pink `[16/20]`; sell 3 emerald -> 1 bed, one separate entry for each of all 16 colors `[12/10]` | `n=21`, add 2, `p=2/21` |
| 4 | six separate buys of 12 dye -> 1 emerald: brown, purple, blue, green, magenta, cyan `[16/30]`; sell 3 emerald -> 1 banner, one separate entry for each of all 16 colors `[12/15]` | `n=22`, add 2, `p=1/11` |
| 5 | sell 2 emerald -> 3 painting `[12/30]` | `n=1`, add 1, `p=1` |

Color variants are independent array slots, not a weighted color choice inside one factory.

### Fletcher

| Level | Source pool | Selection |
| ---: | --- | --- |
| 1 | buy 32 stick -> 1 emerald `[16/2]`; sell 1 emerald -> 16 arrow `[12/1]`; convert 10 gravel + 1 emerald -> 10 flint `[12/1]` | `n=3`, add 2, `p=2/3` |
| 2 | buy 26 flint -> 1 emerald `[12/10]`; sell 2 emerald -> bow `[12/5]` | `n=2`, add 2, `p=1` |
| 3 | buy 14 string -> 1 emerald `[16/20]`; sell 3 emerald -> crossbow `[12/10]` | `n=2`, add 2, `p=1` |
| 4 | buy 24 feather -> 1 emerald `[16/30]`; sell a randomly enchanted bow, base emerald cost 2 `[3/15]` | `n=2`, add 2, `p=1` |
| 5 | buy 8 tripwire hook -> 1 emerald `[12/30]`; sell a randomly enchanted crossbow, base emerald cost 3 `[3/15]`; convert 5 arrow + 2 emerald -> 5 tipped arrow with a random potion `[12/30]` | `n=3`, add 2, `p=2/3` |

### Librarian

| Level | Source pool | Selection |
| ---: | --- | --- |
| 1 | buy 24 paper -> 1 emerald `[16/2]`; emeralds + book -> enchanted book from `EnchantmentTags.TRADEABLE` `[12/1]`; sell 9 emerald -> bookshelf `[12/1]` | `n=3`, add 2, `p=2/3` |
| 2 | buy 4 book -> 1 emerald `[12/10]`; emeralds + book -> enchanted book from `TRADEABLE` `[12/5]`; sell 1 emerald -> lantern `[12/5]` | `n=3`, add 2, `p=2/3` |
| 3 | buy 5 ink sac -> 1 emerald `[12/20]`; emeralds + book -> enchanted book from `TRADEABLE` `[12/10]`; sell 1 emerald -> 4 glass `[12/10]` | `n=3`, add 2, `p=2/3` |
| 4 | buy 2 writable book -> 1 emerald `[12/30]`; emeralds + book -> enchanted book from `TRADEABLE` `[12/15]`; sell 5 emerald -> clock `[12/15]`; sell 4 emerald -> compass `[12/15]` | `n=4`, add 2, `p=1/2` |
| 5 | sell 20 emerald -> name tag `[12/30]` | `n=1`, add 1, `p=1` |

The chosen enchantment, its level, and the emerald price are randomized only if the enchanted-book listing itself is selected. See “Factory-internal randomness.”

### Cartographer

| Level | Source pool | Selection |
| ---: | --- | --- |
| 1 | buy 24 paper -> 1 emerald `[16/2]`; sell 7 emerald -> empty map `[12/1]` | `n=2`, add 2, `p=1` |
| 2 | buy 11 glass pane -> 1 emerald `[16/10]`; 13 emerald + compass -> ocean-monument map `[12/5]` | `n=2`; both factories are attempted, but add only 1 if the map search fails |
| 3 | buy 1 compass -> 1 emerald `[12/20]`; 14 emerald + compass -> woodland-mansion map `[12/10]`; 12 emerald + compass -> trial-chambers map `[12/10]` | `n=3`; add 2 when at least two factories succeed; conditional probability is `2/s` among `s` successful factories |
| 4 | sell 7 emerald -> item frame `[12/15]`; sell 3 emerald -> banner, one separate entry for each of all 16 colors `[12/15]` | `n=17`, add 2, `p=2/17` |
| 5 | sell 8 emerald -> globe banner pattern `[12/30]` | `n=1`, add 1, `p=1` |

Map success is not a fixed random percentage. It depends on a server-side nearest-structure search and therefore on the world, structure tags/configuration, generated structures, and the “skip known structures” condition.

### Cleric

| Level | Source pool | Selection |
| ---: | --- | --- |
| 1 | buy 32 rotten flesh -> 1 emerald `[16/2]`; sell 1 emerald -> 2 redstone `[12/1]` | `n=2`, add 2, `p=1` |
| 2 | buy 3 gold ingot -> 1 emerald `[12/10]`; sell 1 emerald -> 1 lapis lazuli `[12/5]` | `n=2`, add 2, `p=1` |
| 3 | buy 2 rabbit foot -> 1 emerald `[12/20]`; sell 4 emerald -> glowstone `[12/10]` | `n=2`, add 2, `p=1` |
| 4 | buy 4 turtle scute -> 1 emerald `[12/30]`; buy 9 glass bottle -> 1 emerald `[12/30]`; sell 5 emerald -> ender pearl `[12/15]` | `n=3`, add 2, `p=2/3` |
| 5 | buy 22 nether wart -> 1 emerald `[12/30]`; sell 3 emerald -> experience bottle `[12/30]` | `n=2`, add 2, `p=1` |

### Armorer

| Level | Source pool | Selection |
| ---: | --- | --- |
| 1 | buy 15 coal -> 1 emerald `[16/2]`; sell iron leggings for 7 emerald, boots for 4, helmet for 5, chestplate for 9 `[12/1 each]` | `n=5`, add 2, `p=2/5` |
| 2 | buy 4 iron ingot -> 1 emerald `[12/10]`; sell bell for 36 emerald, chainmail boots for 1, chainmail leggings for 3 `[12/5 each]` | `n=4`, add 2, `p=1/2` |
| 3 | buy 1 lava bucket -> 1 emerald `[12/20]`; buy 1 diamond -> 1 emerald `[12/20]`; sell chainmail helmet for 1 emerald, chainmail chestplate for 4, shield for 5 `[12/10 each]` | `n=5`, add 2, `p=2/5` |
| 4 | sell randomly enchanted diamond leggings with base cost 14; randomly enchanted diamond boots with base cost 8 `[3/15 each]` | `n=2`, add 2, `p=1` |
| 5 | sell randomly enchanted diamond helmet with base cost 8; randomly enchanted diamond chestplate with base cost 16 `[3/30 each]` | `n=2`, add 2, `p=1` |

### Weaponsmith

| Level | Source pool | Selection |
| ---: | --- | --- |
| 1 | buy 15 coal -> 1 emerald `[16/2]`; sell iron axe for 3 emerald `[12/1]`; sell randomly enchanted iron sword with base cost 2 `[3/1]` | `n=3`, add 2, `p=2/3` |
| 2 | buy 4 iron ingot -> 1 emerald `[12/10]`; sell bell for 36 emerald `[12/5]` | `n=2`, add 2, `p=1` |
| 3 | buy 24 flint -> 1 emerald `[12/20]` | `n=1`, add 1, `p=1` |
| 4 | buy 1 diamond -> 1 emerald `[12/30]`; sell randomly enchanted diamond axe with base cost 12 `[3/15]` | `n=2`, add 2, `p=1` |
| 5 | sell randomly enchanted diamond sword with base cost 8 `[3/30]` | `n=1`, add 1, `p=1` |

### Toolsmith

| Level | Source pool | Selection |
| ---: | --- | --- |
| 1 | buy 15 coal -> 1 emerald `[16/2]`; sell stone axe, shovel, pickaxe, or hoe for 1 emerald, each a separate entry `[12/1 each]` | `n=5`, add 2, `p=2/5` |
| 2 | buy 4 iron ingot -> 1 emerald `[12/10]`; sell bell for 36 emerald `[12/5]` | `n=2`, add 2, `p=1` |
| 3 | buy 30 flint -> 1 emerald `[12/20]`; sell randomly enchanted iron axe with base cost 1, shovel with base cost 2, or pickaxe with base cost 3 `[3/10 each]`; sell diamond hoe for 4 emerald `[3/10]` | `n=5`, add 2, `p=2/5` |
| 4 | buy 1 diamond -> 1 emerald `[12/30]`; sell randomly enchanted diamond axe with base cost 12 or shovel with base cost 5 `[3/15 each]` | `n=3`, add 2, `p=2/3` |
| 5 | sell randomly enchanted diamond pickaxe with base cost 13 `[3/30]` | `n=1`, add 1, `p=1` |

### Butcher

| Level | Source pool | Selection |
| ---: | --- | --- |
| 1 | buy 14 chicken -> 1 emerald `[16/2]`; buy 7 porkchop -> 1 emerald `[16/2]`; buy 4 rabbit -> 1 emerald `[16/2]`; sell 1 emerald -> rabbit stew `[12/1]` | `n=4`, add 2, `p=1/2` |
| 2 | buy 15 coal -> 1 emerald `[16/2]`; sell 1 emerald -> 5 cooked porkchop `[16/5]`; sell 1 emerald -> 8 cooked chicken `[16/5]` | `n=3`, add 2, `p=2/3` |
| 3 | buy 7 mutton -> 1 emerald `[16/20]`; buy 10 beef -> 1 emerald `[16/20]` | `n=2`, add 2, `p=1` |
| 4 | buy 10 dried kelp block -> 1 emerald `[12/30]` | `n=1`, add 1, `p=1` |
| 5 | buy 10 sweet berries -> 1 emerald `[12/30]` | `n=1`, add 1, `p=1` |

### Leatherworker

| Level | Source pool | Selection |
| ---: | --- | --- |
| 1 | buy 6 leather -> 1 emerald `[16/2]`; sell randomly dyed leather leggings for 3 emerald; randomly dyed leather chestplate for 7 `[12/1 each]` | `n=3`, add 2, `p=2/3` |
| 2 | buy 26 flint -> 1 emerald `[12/10]`; sell randomly dyed leather helmet for 5 emerald; randomly dyed leather boots for 4 `[12/5 each]` | `n=3`, add 2, `p=2/3` |
| 3 | buy 9 rabbit hide -> 1 emerald `[12/20]`; sell randomly dyed leather chestplate for 7 emerald `[12/1]` | `n=2`, add 2, `p=1` |
| 4 | buy 4 turtle scute -> 1 emerald `[12/30]`; sell randomly dyed leather horse armor for 6 emerald `[12/15]` | `n=2`, add 2, `p=1` |
| 5 | sell saddle for 6 emerald `[12/30]`; sell randomly dyed leather helmet for 5 emerald `[12/30]` | `n=2`, add 2, `p=1` |

The level-3 chestplate really carries XP 1 in the source; it uses the two-argument constructor rather than a level-3 XP value.

### Mason

| Level | Source pool | Selection |
| ---: | --- | --- |
| 1 | buy 10 clay ball -> 1 emerald `[16/2]`; sell 1 emerald -> 10 brick `[16/1]` | `n=2`, add 2, `p=1` |
| 2 | buy 20 stone -> 1 emerald `[16/10]`; sell 1 emerald -> 4 chiseled stone bricks `[16/5]` | `n=2`, add 2, `p=1` |
| 3 | buy 16 granite, andesite, or diorite -> 1 emerald, three separate entries `[16/20 each]`; sell 1 emerald -> 4 dripstone block, polished andesite, polished diorite, or polished granite, four separate entries `[16/10 each]` | `n=7`, add 2, `p=2/7` |
| 4 | buy 12 quartz -> 1 emerald `[12/30]`; sell 1 emerald -> 1 terracotta, one separate entry for each of all 16 colors `[12/15]`; sell 1 emerald -> 1 glazed terracotta, one separate entry for each of all 16 colors `[12/15]` | `n=33`, add 2, `p=2/33` |
| 5 | sell 1 emerald -> quartz pillar `[12/30]`; sell 1 emerald -> quartz block `[12/30]` | `n=2`, add 2, `p=1` |

## Trade-rebalance overrides (`VillagerTrades.EXPERIMENTAL_TRADES`)

`FeatureFlags.TRADE_REBALANCE` is not part of the normal pool. When enabled, it replaces the **entire** profession-level map for librarian, armorer, and cartographer. The other ten professions still use the normal tables above.

### Experimental librarian

Levels 1-3 have the same three shapes and configured prices as normal librarian, but the book entry is `commonBooks(xp)`: a `TypeSpecificTrade` selecting the villager type's `TRADES_<TYPE>_COMMON` enchantment tag. Level 4 removes the enchanted-book candidate. Level 5 adds a type-specific special book.

| Level | Source pool | Selection |
| ---: | --- | --- |
| 1 | buy 24 paper; type-common enchanted book; sell bookshelf | `n=3`, add 2, `p=2/3` |
| 2 | buy 4 book; type-common enchanted book; sell lantern | `n=3`, add 2, `p=2/3` |
| 3 | buy 5 ink sac; type-common enchanted book; sell 4 glass | `n=3`, add 2, `p=2/3` |
| 4 | buy 2 writable book; sell clock; sell compass | `n=3`, add 2, `p=2/3` |
| 5 | type-special enchanted book; sell name tag | `n=2`, add 2, `p=1` |

The type mapping covers desert, jungle, plains, savanna, snow, swamp, and taiga. Special books clamp the selected enchantment level to exactly III for desert/plains/savanna, exactly II for jungle/taiga, and the enchantment's full allowed range for snow/swamp. The tag still controls which enchantment is selected. If a tag is empty, `EnchantBookForEmeralds` returns a one-emerald fallback offer for an ordinary book rather than null.

### Experimental armorer

The physical array contains type wrappers at levels 2, 4, and 5. Mismatched wrappers return null and are skipped. In the unmodified seven-type data, every type nevertheless has at least two eligible entries, so two offers are added.

| Level | Source pool/effective type pool | Selection |
| ---: | --- | --- |
| 1 | buy 15 coal `[12/2]`; buy 5 iron ingot `[12/2]` | source `n=2`; both |
| 2 | Eight wrappers: iron boots/helmet/leggings/chestplate for 4/5/7/9 emerald in desert, plains, savanna, snow, taiga; the same four chainmail pieces and prices in jungle/swamp `[12/5]` | source `n=8`; 4 eligible per type; add 2; each eligible `p=1/2` |
| 3 | buy lava bucket `[12/20]`; sell shield for 5 emerald `[12/10]`; sell bell for 36 emerald `[12/10]` | `n=3`, add 2, `p=2/3` |
| 4 | Type pools listed below; every offer has max uses 3 and XP 15 | source `n=26`; normally 4 eligible (`p=1/2`), except snow has 2 (both) |
| 5 | Three eligible entries for every type, listed below; provider offers have max uses 3 and XP 30, block buys have max uses 12 and XP 30 | source `n=16`; add 2; each eligible `p=2/3` |

Level 4 effective pools:

- Desert: iron boots 8, helmet 9, leggings 11, chestplate 13 emerald; each gets Thorns I.
- Plains: the same four iron pieces/prices; each gets Protection I.
- Savanna: iron boots 2, helmet 3, leggings 5, chestplate 7 emerald; each gets Curse of Binding I.
- Snow: iron boots 8 with Frost Walker I; iron helmet 9 with Aqua Affinity I.
- Jungle: chainmail boots 8, helmet 9, leggings 11, chestplate 13 emerald; each gets Unbreaking I.
- Swamp: the same four chainmail pieces/prices; each gets Mending I.
- Taiga: convert diamond boots + 4 emerald -> diamond leggings; diamond leggings + 4 -> diamond chestplate; diamond helmet + 4 -> diamond boots; diamond chestplate + 2 -> diamond helmet. These outputs have no enchantment provider.

Level 5 effective pools:

- Desert: 4 diamond + 16 emerald -> diamond chestplate with Thorns I; 3 diamond + 16 -> diamond leggings with Thorns I; buy 1 iron block -> 4 emerald.
- Plains: 3 diamond + 16 emerald -> diamond leggings with Protection I; 2 diamond + 12 -> diamond boots with Protection I; buy 1 iron block -> 4 emerald.
- Savanna: 2 diamond + 6 emerald -> diamond helmet with Curse of Binding I; 3 diamond + 8 -> diamond chestplate with Curse of Binding I; buy 1 iron block -> 4 emerald.
- Snow: 2 diamond + 12 emerald -> diamond boots with Frost Walker I; 3 diamond + 12 -> diamond helmet with Aqua Affinity I; buy 1 iron block -> 4 emerald.
- Jungle: sell chainmail helmet for 9 emerald with Projectile Protection I; chainmail boots for 8 with Feather Falling I; buy 1 iron block -> 4 emerald.
- Swamp: sell chainmail helmet for 9 emerald with Respiration I; chainmail boots for 8 with Depth Strider I; buy 1 iron block -> 4 emerald.
- Taiga: 4 diamond + 18 emerald -> diamond chestplate with Blast Protection I; 3 diamond + 18 -> diamond leggings with Blast Protection I; buy 1 diamond block -> 42 emerald.

These experimental armor enchantments are not random enchantment-table rolls. `TradeRebalanceEnchantmentProviders#bootstrap` registers a `SingleEnchantment` with a constant level of I for every referenced provider, so the configured result above is deterministic.

### Experimental cartographer

Levels 1 and 4 are the normal pools. Level 3 changes the ocean map's XP to 10 and omits the woodland map; level 5 pairs the globe pattern with a one-use woodland map. Level 2 supplies type-dependent village/explorer maps.

| Level | Source pool | Selection |
| ---: | --- | --- |
| 1 | normal paper and empty-map entries | `n=2`, both |
| 2 | buy 11 glass pane; type-map slot A; type-map slot B; type-map slot C | source `n=4`; choose the first 2 non-null/successful entries in random order |
| 3 | buy 1 compass; ocean-monument map; trial-chambers map `[map XP 10]` | `n=3`; conditional `2/s` among successful factories |
| 4 | normal item-frame plus 16 banner entries | `n=17`, add 2, `p=2/17` |
| 5 | globe banner pattern `[12/30]`; 14 emerald + compass -> woodland-mansion map `[1/30]` | `n=2`; add 2 if the map succeeds, otherwise only the pattern |

Level-2 map slots all cost 8 emerald plus a compass, with max uses 12 and XP 5:

| Villager type | Slot A | Slot B | Slot C |
| --- | --- | --- | --- |
| Desert | savanna village | plains village | jungle temple |
| Savanna | plains village | desert village | jungle temple |
| Plains | taiga village | savanna village | unconditional failure |
| Taiga | snowy village | plains village | swamp hut |
| Snow | plains village | taiga village | swamp hut |
| Jungle | savanna village | desert village | swamp hut |
| Swamp | snowy village | taiga village | jungle temple |

Slot C for plains is explicitly `FailureItemListing`, so it always returns null. Every real map can also return null when its structure search fails. Thus a single unconditional percentage for a level-2 map is not derivable. Conditional on exactly `s` of glass/A/B/C succeeding, each success is selected with probability 1 if `s <= 2` or `2/s` if `s > 2`.

## Factory-internal randomness and failure conditions

Pool selection and offer materialization are separate random stages. The source implementations used by standard professions behave as follows.

### Fixed factories

`EmeraldForItems`, ordinary `ItemsForEmeralds`, ordinary `ItemsAndEmeraldsToItems`, and `SuspiciousStewForEmerald` return a fixed configured offer and do not consume the supplied random source. Stew effects are already fixed in each candidate.

`ItemsForEmeralds` and `ItemsAndEmeraldsToItems` can alternatively name an `EnchantmentProvider`. They call `EnchantmentHelper#enchantItemFromProvider` using the current local difficulty and the supplied random source. In the experimental armorer pools used here, every provider is a constant level-I `SingleEnchantment`, so the visible enchantment result is deterministic. A data pack can replace provider registry contents, so that statement applies to the mapped vanilla bootstrap data.

### Randomly dyed armor

`DyedArmorForEmeralds` always chooses one dye uniformly by `nextInt(16)`. It independently evaluates two more gates in sequence:

- add a second random dye when `nextFloat() > 0.7F`;
- add a third random dye when `nextFloat() > 0.8F`.

Each added dye is independently chosen with `nextInt(16)`, so repeats are possible. The nominal one/two/three-dye gate probabilities are 56%/38%/6% under independent uniform draws, but the exact final RGB-color probabilities are not usefully expressible as those values: `DyedItemColor#applyDyes` mixes channels, brightness-normalizes, rounds, and can receive repeated dyes. The source predicates above are the exact behavior; no final-color percentage is guessed here.

### Enchanted books

`EnchantBookForEmeralds` performs these steps:

1. Uniformly select one holder from the runtime enchantment tag supplied by the listing (`TRADEABLE` or a trade-rebalance type tag).
2. Intersect the enchantment's legal level range with the listing's configured minimum/maximum, then uniformly select an integer level `L` from that inclusive range.
3. Choose the pre-adjustment emerald price uniformly as `2 + nextInt(5 + 10L) + 3L`, i.e. from `2 + 3L` through `6 + 13L` inclusive.
4. Double that price when the enchantment is in `EnchantmentTags.DOUBLE_TRADE_PRICE`, then clamp to 64.
5. Require one ordinary book as the second input and return the enchanted book.

If the tag is empty, the factory does not return null: it returns an ordinary book for 1 emerald plus 1 book.

An exact unconditional enchantment or price probability is not a Java constant. It depends on the runtime registry and tag membership, which data packs may replace. Conditional on a known tag snapshot and selected enchantment, the level and pre-clamp price distributions follow the uniform formulas above; clamping can combine multiple raw prices at 64.

### Randomly enchanted equipment

`EnchantedItemForEmeralds` chooses an enchantment power `P = 5 + nextInt(15)`, uniformly 5-19. It enchants a fresh item through `EnchantmentHelper#enchantItem`, restricting candidates to `EnchantmentTags.ON_TRADED_EQUIPMENT`. The emerald cost is `min(baseCost + P, 64)`.

The power and resulting base price are exact, but an exact output-enchantment distribution cannot be derived from this factory alone. It depends on the runtime enchantment tag and definitions, item enchantability, compatibility filtering, weighted enchantment selection, and additional-selection rolls inside `EnchantmentHelper`. Data packs can change the relevant registry data.

### Tipped arrows

`TippedArrowForItemsAndEmeralds` enumerates the runtime potion registry, keeps potions with at least one effect that the current `PotionBrewing` instance considers brewable, and chooses one retained holder uniformly with `Util#getRandom`. The mapped fletcher trade then sets that potion on all five output arrows.

The exact per-potion probability is `1/N` for the `N` retained runtime potions. `N` is not embedded in the trade factory and can change with registry/brewing modifications, so this document does not invent a fixed percentage. Vanilla assumes the retained list is non-empty; the factory has no empty-list fallback.

### Treasure maps

`TreasureMapForEmeralds` returns null unless generation occurs in a `ServerLevel`. On the server it calls `ServerLevel#findNearestMapStructure(destinationTag, origin, 100, true)`. If no position is found, it returns null. Otherwise it creates a scale-2 map centered on the structure, renders the biome preview, adds the configured target decoration, assigns the configured translated name, and makes the offer cost the configured emeralds plus one compass.

No probability can be assigned from the factory code alone. The result depends on world seed/generation, the runtime structure tag, search origin/radius, available generated structures, and the `skipKnownStructures=true` rule. The selector's null-skipping behavior is why a cartographer can add fewer than two new offers.

### Villager-type wrappers and explicit failure

`TypeSpecificTrade` looks up the villager's `VillagerType` and delegates to that type's listing. It returns null for a non-villager entity or an unmapped type. It is deterministic apart from whatever its delegate does.

`EmeraldsForVillagerTypeItem` similarly maps type to item. NeoForge disables vanilla's constructor-time "all registered types must be present" check and instead returns null for an unknown type, allowing custom villager types without crashing.

`FailureItemListing` always returns null. Vanilla uses it for plains experimental cartographer slot C.

## Directly related NeoForge hooks

`NeoForgeMod` registers `VillagerTradingManager::loadTrades` on the NeoForge event bus. On a `TagsUpdatedEvent` whose cause is `SERVER_DATA_LOAD`, the manager rebuilds trade lists and posts events.

For standard villagers, `VillagerTradingManager`:

1. Holds a startup snapshot of vanilla `VillagerTrades.TRADES`, copied per profession and level as new arrays but retaining the original `ItemListing` objects.
2. On every server-data reload, creates mutable level 1-5 lists for every registered profession and repopulates them from that snapshot. This prevents changes from the previous reload from accumulating.
3. Posts one `VillagerTradesEvent` per registered profession. The event exposes the mutable `Int2ObjectMap<List<ItemListing>>`, profession, and current `RegistryAccess`.
4. Converts the post-event lists back to arrays and writes a new per-profession map into the public `VillagerTrades.TRADES` map.

There is no per-candidate weight field in `VillagerTradesEvent`, `ItemListing`, or `MerchantOffer`, and NeoForge does not patch `AbstractVillager#addOffersFromItemListings` into a weighted chooser. The event is therefore the correct place to discover and wrap the actual normal pools, but selection interception is still required to make wrapper weights operative.

The manager does not copy, event, or rewrite `EXPERIMENTAL_TRADES`. It also does not materialize offers: random book/equipment/map output remains deferred until a villager actually selects the listing.

## Verification anchors

The findings above were checked directly against these mapped/generated sources:

- `net.minecraft.world.entity.npc.VillagerTrades`: `TRADES`, `EXPERIMENTAL_TRADES`, helper factories, type wrappers, and all configured pool entries.
- `net.minecraft.world.entity.npc.Villager#updateTrades`: feature-flag source selection, current-level lookup, and requested count 2.
- `net.minecraft.world.entity.npc.AbstractVillager#addOffersFromItemListings`: uniform random removal without replacement and null skipping.
- `net.minecraft.world.item.enchantment.providers.TradeRebalanceEnchantmentProviders`: constant experimental armorer enchantments.
- `net.neoforged.neoforge.common.VillagerTradingManager`: vanilla snapshot, reload behavior, event publication, and write-back to `TRADES`.
- `net.neoforged.neoforge.event.village.VillagerTradesEvent`: mutable level lists and registry access.
- `net.neoforged.neoforge.common.NeoForgeMod`: registration of the reload listener.
