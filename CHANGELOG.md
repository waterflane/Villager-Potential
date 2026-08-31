# Changelog

All notable changes to Villager Potential are documented here.
The format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Unreleased

### Trade restocking and prices

- Villagers now restock used offers only after actually waking from sleep;
  workstation activity no longer performs the vanilla twice-daily restock.
- Completed sleep clears accumulated demand and restores demand-adjusted prices.
- Demand never changes an emerald stack or any received result stack. Only
  non-emerald payment stacks grow, by at most 12.5%; integer rounding never
  exceeds that cap.
- Fixed non-emerald payments becoming cheaper under positive demand, including
  Forge trades such as wheat for an emerald.
- Non-emerald payment counts are now monotonic between completed sleeps on both
  loaders: after demand raises a payment, decay and later recalculation cannot
  lower the already reached count.
- Prices now update in the open merchant screen immediately after an ordinary
  purchase has completed at its previous price. Only the exact offer used by
  that result slot is repriced; other rows remain untouched.
- Removed the special Shift-click pricing context and its config toggle. Every
  successful result-slot action now follows the same single post-purchase
  recalculation path.
- Percentage settings now cap the total price relative to immutable base counts,
  including vanilla adjustments, instead of limiting only this mod's added
  delta.
- Added `economy.price.demandScoreForMaximumPrice` (default `8.0`) so frequently
  used offers can realistically reach their configured percentage cap before
  the villager sleeps.
- Clarified that `economy.price.enabled` defaults to `true` and disables the
  complete demand price-increase system when set to `false`.

### Forge 1.20.1 port

- Fixed a ticking-entity crash when a zombie infects a villager after Forge has
  already invalidated the discarded villager's capabilities.
- Added a Java 17 Forge module targeting Minecraft 1.20.1, Forge 47.4.23, and
  the supported Forge range 47.4.10 through 47.x.
- Ported capabilities, conversion copying, lifecycle/trade hooks, commands,
  server configuration, specialization reloads, mixins, integration events,
  SimpleChannel synchronization, and the MerchantScreen progress overlay.
- Added schema 11 portable entity NBT (`villager_potential:data`) shared by
  Forge capabilities and NeoForge attachments. Equal-schema conflicts prefer
  the portable container so externally transferred saves converge.
- Added versioned portable trade metadata, schema-10 migration and collision
  merging, and conservative handling of unknown modded item metadata.
- Moved shared localization and progress calculations into `core` and added
  Forge unit/GameTest and release-JAR verification.

## 1.0 — first public release

Initial feature set for Minecraft 1.21.1 with NeoForge.

### Villager identity

- Individual profession aptitudes: every villager receives one immutable
  progression-speed multiplier per known profession, generated from a bounded
  normal distribution around neutral 1.0 (default spread ±0.3, clamped to
  0.5–2.0).
- Rare talents: with a small chance (default 2%) one aptitude is drawn from an
  exceptional upper tail at least 3 standard deviations above the mean.
- Genetic inheritance: bred children blend both parents' aptitudes (default
  70% parent average, 20% fresh generation) with zero-mean mutations;
  children never inherit careers, skill, trades, or demand.
- Optional qualitative tiers (`Poor` … `Exceptional`) derived from the
  configured distribution.

### Progression

- Time-based professional skill: eligible loaded employment ticks accumulate
  persistent skill at base rate × aptitude × activity (default gates:
  adults only; a valid owned workstation is mandatory, with optional working
  activity checks).
- Progression uses the shared Minecraft server clock (20 ticks per second) and
  counts only daytime ticks 0–11,999; villagers gain no professional experience
  at night.
- Losing the workstation immediately suspends skill gain and trading (including
  an already open menu) and releases the profession so a new workstation and
  profession can be claimed.
- Vanilla profession levels follow inclusive skill thresholds
  (Novice … Master, defaults 0 / 1.5 / 3.5 / 6.5 / 10.5). At neutral aptitude
  and activity, the base interval sizes are 1.5 / 2 / 3 / 4 Minecraft workdays;
  level-ups unlock offers and keep the vanilla regeneration effect.
- Professional rate compounds by level: ×1.00 / ×1.20 / ×1.44 / ×2.16 / ×3.24.
  The first two promotions increase the previous rate by ×1.2 and the final two
  by ×1.5; the blue-bar tooltip shows both this multiplier and estimated minutes
  remaining at the effective current rate.
- Progress tooltips use restrained semantic colors: blue/aqua for professional
  coefficients and experience, green for trade multipliers, and yellow for
  remaining values.
- The blue tooltip exposes each villager's effective aptitude for the current
  profession separately from the shared level-rate bonus.
- The compact rate line now shows
  `base × aptitude × purchases × level = current exp/min`.
- Trade experience points no longer schedule profession levels.
- Existing villagers bootstrap their learned skill to their current vanilla
  level and are never demoted.
- Trading accelerates rather than teaches: successful trades raise a
  per-profession purchase multiplier (default +0.1 up to ×2.0) that persists
  through the current level and resets to ×1.0 after promotion. Each level
  requires 20% more successful trades for the same multiplier increase.
- Professional specialization: villagers store one named specialization per
  profession on first employment; specialization bias toward configured trade
  categories strengthens with professional skill (curve exponent and bounds
  configurable, per-profession strength overrides supported).

### Trades and economy

- Learned evolving trades with a default `PERSISTENT` palette: generated
  stable trades stay learned per profession and are restored verbatim,
  including across workstation loss, profession changes, and config reloads.
- Profession switches retain each profession's skill, earned vanilla level,
  specialization, purchase multiplier, learned offers, and usage history. Data
  and regeneration work are bounded per profession, and workstation validation
  is cached and never loads missing chunks.
- Optional palette policies `VANILLA`, `WEIGHTED_MEMORY`, `EXHAUST`, and
  `CYCLIC`, tuned by trade-memory recovery times measured in eligible
  profession ticks; mode changes never delete stored knowledge.
- Rare-trade protection: configurable result items can use a shortened
  recovery window so signature trades stay special (off by default).
- Demand-based pricing: per-villager, per-trade demand scores rise with use
  and decay over time; prices interpolate between configurable multipliers
  (defaults ×1.0–×2.0) and layer on top of vanilla's own adjustments.
- Optional demand-driven stock effects (off by default): high demand can
  extend an offer's uses after a genuine vanilla restock within hard caps,
  never creating extra restocks.
- Specialization definitions load from data packs
  (`data/<namespace>/villager_potential/specializations/*.json`, version 1)
  for any profession, with built-in category coverage for all thirteen
  vanilla professions and a safe `villager_potential:general` fallback.

### Server administration

- Single world-owned SERVER configuration with validated defaults and ranges;
  invalid values reject the reload and keep the previous configuration.
- `/villagerpotential inspect <villager>` and `reload` (permission level 2);
  `set aptitude|skill`, `reset profession`, and explicitly destructive
  `regenerate profession|all` (permission level 4).
- Opt-in concise diagnostics under `[debug]`, plus detailed resolved trade
  weight logging; per-tick progression is never logged.
- Obsolete interaction-feedback config was removed after action-bar messages
  were retired; individual aptitude is presented in the trading progress UI.

### Integration

- NeoForge 1.21.1 metadata and build verification cover the supported
  `21.1.233` through `21.1.x` loader range.
- Public read model: `VillagerPotentialApi.view(villager)` returns immutable
  `PotentialView` snapshots covering aptitudes, careers, skill,
  specializations, learned palettes, trade memory, and demand.
- Versioned service SPI `core.api.VillagerPotentialService` (API version 2)
  via `VillagerPotentialServices.forServer(server)` with UUID lookup and a
  deliberately narrow set of explicit mutations.
- Lifecycle hooks: initialization, inheritance, profession changes, batched
  skill changes, applied vanilla level changes, first specialization
  assignment.
- Trade hooks: final candidate-weight modification, new learned-palette
  entries, processing-kind notifications, completed trades, and demand
  changes; all synchronous on the server thread.
- External content compatibility: registered modded professions gain
  deterministic aptitudes, full career state, and persistence; unknown or
  unreadable trade listings degrade to neutral categories and preserve-only
  keys instead of breaking generation.
- Localization: complete `en_us` and matching `ru_ru` message sets.
