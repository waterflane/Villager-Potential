# Villager Potential

<img width="800" height="450" alt="village potential" src="https://github.com/user-attachments/assets/98192a88-ba37-46d0-bbba-1c5c04d68b99" />

**Individual skills, professions and evolving trades for Minecraft villagers.**

Villager Potential replaces anonymous, trade-XP-driven villagers with individuals:
every villager is born with per-profession aptitudes, can inherit them from
parents, earns professional skill through real work time, specializes as it
matures, learns a durable palette of trades, and runs a local market where demand
moves prices.

Supported platforms:

- **Minecraft 1.21.1** with **NeoForge 21.1.233–21.1.x** (built against 21.1.248);
- **Minecraft 1.20.1** with **Forge 47.4.10–47.x** (built against 47.4.23).

Use the JAR whose loader and Minecraft version match the server. Both JARs
embed the same Java-17-compatible domain core and use the same data format.

## How it works

<img width="1919" height="1079" alt="blue_day" src="https://github.com/user-attachments/assets/54db6390-e2b3-4901-ba96-2e5379ffff99" />

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
`base rate × aptitude × activity × level rate`. The cumulative level-rate
multipliers are ×1.00, ×1.20, ×1.44, ×2.16, and ×3.24: the first two promotions
increase the preceding rate by ×1.2 and the final two by ×1.5. Skill thresholds
map onto vanilla's five profession levels (`Novice` … `Master`), so leveling
still unlocks offer slots, regenerates offers, and shows the vanilla level-up
effect. Trade XP can no longer schedule levels; existing villagers never lose a
level they already had. The base threshold gaps are 1.5, 2, 3, and 4 Minecraft
workdays before aptitude, purchase activity, and level-rate acceleration.
All timing uses the server's normal tick clock (`20 ticks = 1 second`, `1,200
ticks = 1 minute`). Only daytime ticks from `0` through `11,999` count; night
ticks never add tenure or professional experience.
Progress tooltips use restrained semantic highlights: blue/aqua for professional
rates and experience, green for trade multipliers, and yellow for remaining
amounts or time. Labels stay neutral so the values remain easy to scan.
The blue tooltip reports the villager's effective aptitude for the current
profession separately from the shared level-rate bonus, so two novices can show
different learning multipliers. Its compact rate line shows
`base × aptitude × purchases × level = current exp/min`.

A villager must still own the correct, loaded workstation. Losing it immediately
stops professional-skill gain, closes an open merchant menu, blocks new trading,
and releases the profession so another workstation can be claimed without
deleting the old career.

### Trading accelerates, it does not teach

Successful trades raise a per-profession **activity** multiplier from ×1 to ×2.
Each promotion makes the same increase require 20% more successful trades, so
the configured per-trade gain is divided by ×1.00 / ×1.20 / ×1.44 / ×1.73 /
×2.07 from Novice through Master.
The purchase bonus remains for the current level and resets to ×1 when the next
level is applied. Activity multiplies the value of elapsed work time — it is
never skill by itself, so trading speeds up professionals without letting AFK
trade halls replace working villagers.

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

Career skill, purchase activity, specialization, learned trade identities, and
trade history are stored independently for every profession. Returning to an
old profession therefore restores its earned level and offers. Storage and
restoration are bounded (128 learned/history entries per profession by default,
plus a fixed regeneration budget), workstation checks are cached, and they
never load an absent chunk; ordinary vanilla villagers only use a small fraction
of those limits.

### Localization formatting

Language values remain ordinary Minecraft translation strings. Native legacy
formatting is supported with the section-sign codes; in JSON, use the explicit
Unicode escape form such as `\u00A79Blue text\u00A7r`. Structured text-component
objects and ampersand aliases such as `&9` are not valid vanilla lang values.
Release verification compares both packaged language maps with their source
files exactly, so missing, extra, or changed translations fail the build.

### Demand-based pricing and optional stock effects

Every stable logical trade carries a demand score that rises with successful
uses. Popular trades become more expensive until the villager sleeps. Offers
never change an emerald stack or the received result stack. Trades paid with
emeralds therefore retain their original item counts, while trades paid with
another item may require up to 12.5% more of that payment item. Integer rounding
never exceeds the cap. By default the percentage cap is reached after eight
demand points, and ordinary purchases update the visible offer immediately
after the old-price purchase finishes. Only the exact offer used by that result
slot is repriced; the villager's other rows remain untouched. There is no
separate Shift-click pricing branch: every successfully completed purchase
action triggers one recalculation after that action. A completed sleep resets
demand pricing and restocks used offers;
workstation activity no longer performs the vanilla twice-daily restock.
Optionally, high demand can also extend how many uses an offer survives until
that sleep restock.

## Server configuration

All gameplay options live in one world-scoped SERVER config with validated
defaults, documented ranges, and safe reload semantics — invalid values reject
the reload and keep the previous configuration. The file is created only after
the world has been loaded: Forge and NeoForge singleplayer use
`.minecraft/saves/<world>/serverconfig/villager_potential-server.toml`, while a
dedicated server uses `<world>/serverconfig/villager_potential-server.toml`.
It is intentionally not placed in the global `.minecraft/config` directory. See
[docs/configuration.md](docs/configuration.md) for every option, its default,
time unit, gameplay effect, and exact per-world file location.

Useful commands (all under `/villagerpotential`, output is diagnostic text):
`inspect <villager>` and `reload` need permission level 2;
`set aptitude|skill`, `reset profession`, and the explicitly destructive
`regenerate profession|all` need permission level 4.

## Integration API (developers)

A small, versioned integration surface is available to other Forge and
NeoForge mods:

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

## Compatibility philosophy

- Villagers keep looking and behaving like vanilla; there is no custom block,
  item, entity, or menu type. The optional client code augments the vanilla
  merchant screen, while dedicated servers remain fully supported.
- Vanilla systems stay authoritative: restock timing, job-site logic, and offer
  regeneration are reused, and prices layer on top of vanilla adjustments.
- Other mods' professions and trades are handled conservatively — registered
  professions integrate fully; anything unrecognized is preserved rather than
  dropped, and ambiguous listings degrade to a neutral category.
- No Bukkit/Paper compatibility is claimed or provided.

## License

Apache 2.0. See [LICENSE](LICENSE).
