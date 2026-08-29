# Villager Potential

**Individual skills, professions and evolving trades for Minecraft villagers.**

Villager Potential replaces anonymous, trade-XP-driven villagers with individuals:
every villager is born with per-profession aptitudes, can inherit them from
parents, earns professional skill through real work time, specializes as it
matures, learns a durable palette of trades, and runs a local market where demand
moves prices.

Supported platform: **Minecraft 1.21.1** with **NeoForge 21.1.233–21.1.x**.
A compatibility build matrix checks both `21.1.233` and the current pinned
`21.1.248` toolchain; one release JAR works across that range.
A Forge 1.20.1 port is planned; it is not supported yet.

## How it works

### Individual aptitudes

When a villager first needs its potential, it receives one immutable aptitude
per known profession — a progression-speed multiplier generated from a bounded
normal distribution around neutral `1.0`. Aptitudes do not change during a
villager's life unless you explicitly regenerate them. Qualitative tiers
(`Poor` … `Exceptional`) exist only for optional player feedback.

### Rare talents

With a small chance one aptitude is drawn from an exceptional upper tail,
producing a genuinely outstanding specialist instead of another average
villager.

### Genetics: inheritance and mutation

Bred children derive their aptitudes from both parents — a parent-average blend
plus a fresh random contribution, plus zero-mean mutations that keep bloodlines
from becoming fully deterministic. Missing parent data falls back to ordinary
generation. Children start with empty careers: skill, trades, and demand are
earned, never inherited.

### Professional skill grows with work time

Eligible loaded server ticks spent employed accumulate professional skill at
`base rate × aptitude × activity`. Skill thresholds map onto vanilla's five
profession levels (`Novice` … `Master`), so leveling still unlocks offer slots,
regenerates offers, and shows the vanilla level-up effect. Trade XP can no
longer schedule levels; existing villagers never lose a level they already had.

### Trading accelerates, it does not teach

Successful trades raise a per-profession **activity** multiplier that decays
back toward baseline over time. Activity multiplies the value of elapsed work
time — it is never skill by itself, so trading speeds up professionals without
letting AFK trade halls replace working villagers.

### Professional specialization

When a villager first enters a profession, one named specialization is selected
and stored on that career. Specializations re-weight the live vanilla candidate
pool toward their configured trade categories (for example a librarian biased
toward enchanted books), and the bias strengthens as professional skill grows.
Definitions ship as data pack JSON; weighting only modifies candidates that
vanilla already offers and can add nothing by itself.

### Learned evolving trades

By default villager offers are **persistent**: what a villager has learned stays
learned. Removing a workstation, changing professions, or editing the config
never rerolls what the villager knows — learned palettes and full trade history
are kept per profession and restored on the next regeneration. Optional
alternative policies change how regenerations treat memory:

| Mode | Behavior |
| --- | --- |
| `PERSISTENT` | Learned trades are kept and restored; new levels only add. |
| `VANILLA` | Vanilla rerolls; history is still recorded. |
| `WEIGHTED_MEMORY` | Recently seen trades are down-weighted, recovering with work time. |
| `EXHAUST` | Recently seen trades are unavailable until they recover. |
| `CYCLIC` | Every candidate appears once per cycle before any repeats. |

Rare results such as mending books can be given shortened recovery so they stay
special.

### Demand-based pricing and optional stock effects

Every stable logical trade carries a demand score that rises with successful
uses and decays toward a baseline over time. Popular trades become more
expensive and unpopular trades cheaper, layered on top of — never replacing —
vanilla's own hero-of-the-village and demand adjustments. Optionally, high
demand can also extend how many uses an offer survives until its next genuine
vanilla restock; this never creates extra restocks or bypasses workstation and
daily timing checks.

## Server configuration

All gameplay options live in one world-scoped SERVER config with validated
defaults, documented ranges, and safe reload semantics — invalid values reject
the reload and keep the previous configuration. See
[docs/configuration.md](docs/configuration.md) for every option, its default,
range, unit, and gameplay effect.

Useful commands (all under `/villagerpotential`, output is diagnostic text):
`inspect <villager>` and `reload` need permission level 2;
`set aptitude|skill`, `reset profession`, and the explicitly destructive
`regenerate profession|all` need permission level 4.

## Integration API (developers)

A small, versioned integration surface is available to other NeoForge mods:

- read model — `VillagerPotentialApi.view(villager)` returns an immutable
  `PotentialView` snapshot (aptitudes, careers, skill, specializations,
  learned palettes, trade memory, demand);
- versioned service SPI — `core.api.VillagerPotentialService` (API version 2)
  obtained from `VillagerPotentialServices.forServer(server)` for UUID-based
  access and a deliberately narrow set of explicit mutations;
- lifecycle events — initialization, inheritance, profession changes, batched
  skill changes, vanilla level changes, first specialization assignment;
- trade hooks — final candidate-weight modification, learned-palette events,
  processing kind notifications, completed trades, demand changes;
- modded professions and trades work through portable fallbacks: unknown
  professions get deterministic aptitudes, foreign offers fall back to safe
  categories, and offers whose identity cannot be established remain available
  through their original trade system.

See [docs/integration-api.md](docs/integration-api.md) for the full contract.
Specialization definitions for data pack authors are documented in
[docs/specialization-definitions.md](docs/specialization-definitions.md).

Optional player feedback (action-bar tier hint on interaction) is disabled by
default; see [docs/player-feedback.md](docs/player-feedback.md).

## Compatibility philosophy

- Villagers keep looking and behaving like vanilla; there is no custom block,
  item, entity, screen, or client requirement, and the mod runs on dedicated
  servers.
- Vanilla systems stay authoritative: restock timing, job-site logic, and offer
  regeneration are reused, and prices layer on top of vanilla adjustments.
- Other mods' professions and trades are handled conservatively — registered
  professions integrate fully; anything unrecognized is preserved rather than
  dropped, and ambiguous listings degrade to a neutral category.
- No Bukkit/Paper compatibility is claimed or provided.

## License

Apache 2.0. See [LICENSE](LICENSE).
