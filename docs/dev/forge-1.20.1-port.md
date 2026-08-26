# Forge 1.20.1 port — audit and plan

Status: **audit complete, port not started**. Current target remains Minecraft 1.21.1 /
NeoForge 21.1.248 (Java 21) with a loader-neutral `core` module pinned to **Java 17**.
This document captures what a future `forge-1.20.1` module can reuse unchanged, the exact
platform adapters still required, and the seams that must isolate loader/version behavior.
It reflects the working tree after the 2026-08 audit pass that moved the last pieces of
loader-neutral domain logic out of `neoforge-1.21.1`.

## 1. Boundary status (verified)

* `core` contains **zero** imports or compile dependencies on `net.minecraft`,
  `net.neoforged`, or `net.minecraftforge`. Its only dependencies are the JDK and Gson
  (`com.google.code.gson`, provided by Minecraft itself at runtime; pinned 2.10.1 for
  standalone tests).
* The boundary is machine-enforced: `gradlew :core:checkPlatformImports` fails the build on
  any forbidden import and runs as part of `:core:check`. The regex self-tests its coverage
  at execution time.
* `core` compiles and tests under a **Java 17 toolchain** (`JavaLanguageVersion.of(17)` in
  `core/build.gradle`). Language audit found nothing above 17: records (16), sealed
  interface (17, `TradeKey` only), one pattern-matching `instanceof`, arrow switch over
  constants, `Stream.toList()` (16), `RandomGenerator` (17). No `Math.clamp` (21),
  no sequenced collections, no pattern-matching switch/record patterns anywhere in core.

## 2. Reusable unchanged

Everything under `org.waterflane.villager_potential.core` is consumed by the Forge module
as-is. By feature:

| System | Core home | Notes |
| --- | --- | --- |
| Potential/aptitudes | `AptitudeGenerator`, `AptitudeTier`, `AptitudeProvisioning`, configs | Pure sampling/classification; caller supplies `RandomGenerator`. |
| Genetics | `AptitudeInheritance` | Dedupes + sorts professions before drawing, so results are order-stable. |
| Career progression | `SkillProgression`, `ProfessionCareerState`, `ProfessionLevelThresholds`, `ProfessionProgressBatch` | Tenure batching (20-tick flush interval) now lives here. |
| Specialization | `ProfessionSpecializationDefinition`, `SpecializationDefinition`, `SpecializationBiasConfig`, `ProfessionSpecializationAssignment` | `selectionModifiersFor(Optional<SpecializationId>)` encodes "general ⇒ neutral weights". |
| Trade weighting/selection | `TradeSelectionResolver`, `TradeMemoryRecovery` | Sampling-without-replacement semantics incl. the uniform fast path that preserves vanilla's RNG draw count. |
| TradeKey/palette | `TradeKey` (sealed), `TradePaletteState`, `TradeHistory`, `TradeKey.isStable/sameShape` | Canonical fallback markers `unregistered:` / `fallback:` are now documented on `TradeKey`; stability and shape-matching rules live in core. |
| Persistent learned trades / reroll modes | `TradePaletteRerollStrategy`, `TradeMemoryRecoveryConfig`, `VillagerPotentialState.observationTimeFor(...)` | Which time base each reroll mode observes (profession time vs game time) is single-sourced in core. |
| Demand/economy | `MarketDemandState`, `MarketDemandPricing`, `MarketDemandStock`, economy configs | Integer price deltas and stock ceilings are pure functions over vanilla-supplied numbers. |
| Config-domain objects | `VillagerPotentialConfig`, `VillagerTradeConfig`, `VillagerPotentialConfiguration` + section records; `SpecializationConfig.parseStrengthOverrides(...)` | The `ns:profession=strength` override format and its error messages are part of the cross-loader contract. |
| Datapack format | `SpecializationDefinitionParser` | Version-neutral specialization JSON (format_version 1): identical parsing/validation on every loader; throws `JsonParseException` with `<source>` context. |
| Deterministic identity | `PotentialSeeds` | World-seed + UUID + salt mixing for initialization, inheritance, specialization, lazy aptitudes. Single source of the salt constants — byte-for-byte parity with values previously private to `neoforge-1.21.1` is mandatory. |
| External API | `core.api`: `PotentialView(s)`, `VillagerPotentialService`, `ListenerRegistration`, `InspectionFormat` | API v2 SPI plus the exact `/villagerpotential inspect` text rendering, shared so admin output cannot drift between loaders. |

## 3. Promoted from `neoforge-1.21.1` during this audit

Moved only logic with zero Minecraft/loader references and real duplication risk:

* Seed derivation (`initializationSeed`/`inheritanceSeed`/`specializationSeed`/
  `lazyAptitudeSeed`, salts, mixhash) → `core.PotentialSeeds`. Platform code now calls it;
  behavior is bit-identical.
* `ProfessionProgressBatch` inner class + interval constant → `core.ProfessionProgressBatch`
  (nullable tracked profession retained: unemployed villagers hold batches).
* Reroll-mode observation time (`tradeMemoryTime`) →
  `VillagerPotentialState.observationTimeFor(ProfessionId, long gameTime,
  TradePaletteRerollStrategy)`.
* Offer-key stability rule (`isStable`) → `TradeKey.isStable`;
  persistent-restoration shape match (`hasSameShape`) → `TradeKey.sameShape`.
* Specialization modifier resolution (`selectionModifiers`) →
  `ProfessionSpecializationDefinition.selectionModifiersFor(...)`.
* Palette-wide newest observation (`latestSeenTime`) → `TradePaletteState.latestSeenTime(...)`.
* Specialization definition JSON parsing (format contract) → `core.SpecializationDefinitionParser`
  (Gson); the NeoForge manager keeps only resource discovery, reload ordering, duplicate
  detection, and logging.
* Config override-string parsing (`parseProfessionOverrides`) →
  `SpecializationConfig.parseStrengthOverrides(List<String>)`.
* Admin inspection formatting (`formatInspection` + summarizers) →
  `core.api.InspectionFormat`; the command class embeds the shared renderer.
* `SpecializedTradeSelection.DEFAULT_MEMORY_RECOVERY` now derives from
  `VillagerTradeConfig.DEFAULT` instead of duplicating literal defaults.

Platform call sites delegate to these APIs; no public NeoForge behavior changed
(`:neoforge-1.21.1:test` green before and after).

## 4. Remaining Forge 1.20.1 work — exact adapters per seam

The port must create a `forge-1.20.1` sibling of `neoforge-1.21.1` implementing the six
seams below. Nothing else should be duplicated.

### 4.1 Persistence
NeoForge stores `VillagerPotentialState` as Data Attachments (`AttachmentType` +
`DeferredRegister`, codec-based serialization, auto-copy across villager↔zombie-villager
conversion). Forge 1.20.1 has no attachment system. Expected adapter:
`AttachCapabilitiesEvent<Entity>` registering an `ICapabilitySerializable` provider keyed by
a capability token wrapping `VillagerPotentialState`; serialize through the same DFU codecs
(`Codec#encodeStart(NbtOps.INSTANCE, ...)`) that `VillagerPotentialAttachments` already
defines — those codec definitions are copy-portable, but they stay **per-platform**: do not
lift them into core or invent a shared persistence facade. Conversion copying
(`Zombie#killedEntity` → `finishConversion`, cure path) must be handled manually on Forge
(e.g. stashing state during `LivingConversionEvent`), where NeoForge copies attachments
implicitly. Schema migration stays `VillagerPotentialState.migrate(...)` in core.

### 4.2 Event hooks
Map each hook in `VillagerPotentialEvents` / lifecycle & trade event buses to its Forge
equivalent (Forge 1.20.1 uses `TickEvent.EntityTickEvent` with a phase field instead of
`EntityTickEvent.Pre/Post` classes, `MinecraftForge.EVENT_BUS` registration instead of
`@EventBusSubscriber(bus = ...)` splits). The new-villager set, profession-change polling,
tenure batching, and trade recording flows are unchanged — only registration plumbing and
event types differ. Semantic lifecycle/trade listener lists
(`VillagerPotentialLifecycleEvents`, `VillagerPotentialTradeEvents`) carry `Villager`
references and remain platform-owned.

### 4.3 Vanilla trade adapters
Reimplement, do not share: `MerchantOfferTradeKeys` (1.20.1 has **no data components** —
identity must come from NBT; define the canonical component-string encoding for keys, see
§5), `ClassifiedItemListing` + `VanillaTradeClassifications` (re-snapshot 1.20.1
`VillagerTrades` pool layouts; category *keys* are stable, layout indices are not),
`SpecializedTradeSelection`'s pool access (1.20.1 has no
`FeatureFlags.TRADE_REBALANCE`/`EXPERIMENTAL_TRADES` branch — delete it), and demand
pricing/restock application points (`updateSpecialPrices` RETURN, `restock` RETURN,
`MerchantOffer` ceiling mixin — target methods all exist under the same names in 1.20.1;
set mixin config `compatibilityLevel` to `JAVA_17`).

### 4.4 Registry/profession conversion
Port `VillagerProfessionIds` (registry lookup differs slightly:
`new ResourceLocation(ns, path)` instead of 1.21 factories) and keep the supported-vanilla
profession list platform-side. **Parity requirement:** `initialize(worldSeed, uuid)`
iterates this list in declaration order while drawing seeded aptitudes; the Forge list must
declare the same 13 professions in the same order or initialized aptitudes diverge from
NeoForge saves.

### 4.5 Config integration
`ServerConfig`/`Config` translate `ModConfigSpec` (NeoForge) values into
`VillagerPotentialConfiguration`. Forge 1.20.1 uses `ForgeConfigSpec` with the same builder
shape. Port spec bindings, the reload-event handler, and the `Values`→core mapping calls;
validation and override parsing are already core. Keep TOML keys, ranges, and defaults
identical so user configs and docs transfer verbatim.

### 4.6 Commands / network / client
Commands: Brigadier tree is portable; re-register against Forge's
`RegisterCommandsEvent` and keep embedding `core.api.InspectionFormat` so output matches
byte-for-byte. GameTests: rewrite `VillagerProgressionGameTests` against Forge's gametest
registration. Networking/client: the mod currently adds no custom packets or screens
(feedback is server-authored action-bar text), so no network/client layer needs porting;
if one is added later, it belongs behind this same seam.

## 5. Known Minecraft-version differences (from current architecture)

1. **Item stacks**: `TradeKey.Item.components` is a canonical *data-component* string
   (1.20.5+ concept). 1.20.1 offers carry arbitrary NBT; the Forge adapter must choose and
   freeze a canonical encoding (e.g. sorted SNBT of the stack tag) or persisted keys,
   palettes, and demand entries will not round-trip across loaders sharing one world.
2. **ResourceLocation API**: constructors/factories differ (`new ResourceLocation(...)` on
   1.20.1 vs `fromNamespaceAndPath`/`withDefaultNamespace` on 1.21.1). Contained inside
   `VillagerProfessionIds`/`MerchantOfferTradeKeys`.
3. **Trade rebalance experiment**: `FeatureFlags.TRADE_REBALANCE` +
   `VillagerTrades.EXPERIMENTAL_TRADES` do not exist on 1.20.1; the selection adapter falls
   back to standard pools only.
4. **Enchantment/book listings**: enchanted-book generation paths changed after 1.20.1;
   classification snapshots must be re-derived from the 1.20.1 `VillagerTrades` arrays
   rather than copied.
5. **Event model**: NeoForge split tick events into `Pre/Post` types and moved village
   trade wrapping to `VillagerTradesEvent`; Forge 1.20.1 equivalents have different shapes
   but the same semantics (see §4.2–4.3).
6. **Persistence host**: attachments (NeoForge) vs capabilities+CNBT (Forge) — §4.1.
7. Unchanged and therefore safe to assume identically on 1.20.1: the five-tier villager
   level ladder and XP thresholds, `MerchantOffer` price/demand/maxUses fields, restock and
   special-price flow, breeding/conversion flow, brain memory/activity eligibility signals.

## 6. Java 17 constraints

* `core` must stay compilable as **Java 17**: allowed — records, sealed types,
  pattern-matching `instanceof`, arrow switches, `RandomGenerator`, `Stream.toList()`,
  `String.isBlank()`; forbidden — `Math.clamp`, `SequencedCollection`/`getFirst()`/
  `getLast()`/`reversed()`, pattern-matching switch, record patterns, unnamed variables,
  string templates, anything from Java 18+.
* When promoting more code from `neoforge-1.21.1` later, remember the NeoForge module
  legitimately uses Java 21 APIs (e.g. `List.getFirst()` in game tests); scrub them before
  moving shared logic into core. `:core:check` fails fast via the toolchain either way.
* The future `forge-1.20.1` module itself must target Java 17 (Forge 1.20.1 requirement);
  only the NeoForge module may stay on 21.

## 7. Deliberately not done (per port policy)

* No `forge-1.20.1` module created yet.
* No artificial common persistence API over attachments/capabilities — the shared boundary
  is `VillagerPotentialState` itself; serialization glue stays per-loader.
* No redesign of working APIs for symmetry: platform facades
  (`VillagerPotentialApi`, services, events, commands) keep their shapes and simply
  delegate the neutral parts.
* NeoForge implementation not weakened: all changes were extraction + delegation; the full
  NeoForge test suite passes unchanged.

## 8. Validation checklist (run after any further port step)

```text
gradlew :core:check                      # Java 17 compile + tests + checkPlatformImports
gradlew :core:checkPlatformImports       # explicit architecture boundary gate
gradlew :neoforge-1.21.1:build           # NeoForge 1.21.1 build + unit tests
git diff --check                         # whitespace hygiene
```

All four passed on the audited tree (core: 162 tests; neoforge: 128 tests).
