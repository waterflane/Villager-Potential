# Server configuration reference

Villager Potential ships a single **SERVER** config (`SERVER` type in NeoForge
terms). It is owned by each world, so different worlds can use different
settings. The file is created at
`<world>/serverconfig/villager_potential-server.toml` on first world load, with
defaults shown below also available as templates under
`<world>/serverconfig/defaultconfigs/`.

Conventions used in this document:

- **Default** is the shipped value; **Range** is enforced by validation.
- *Server tick*: 1/20 of a second of loaded server time.
- *Eligible profession tick*: a loaded server tick that counts toward tenure
  under the current `[career]` gates (see that section).
- *Aptitude* values are progression-speed multipliers where `1.0` is vanilla
  speed.
- *Skill* values are abstract points accumulated by time; level thresholds are
  expressed in the same points.

## Applying changes

The config is validated into an immutable snapshot whenever it loads or
reloads. Invalid values fail validation: the reload is rejected, the previous
valid configuration stays active, and nothing is written to saved villager
state. Two ways to apply edits to a running world:

- restart or reconnect the integrated server, or
- run `/villagerpotential reload` (permission level 2), which validates the new
  file first and then reloads server resources (including specialization
  definitions); if either step fails, the previous configuration remains.

Mode changes and other edits never delete stored villager knowledge: learned
palettes, history, careers, and demand are preserved even when the options that
produced them are disabled or lowered.

---

## `[aptitude]` — immutable individual potential

| Option | Type | Default | Range | Effect |
| --- | --- | --- | --- | --- |
| `enabled` | bool | `true` | — | Generates immutable per-profession aptitudes for villagers that lack them. `false` gives new villagers neutral `1.0` aptitudes without rerolling existing ones. |
| `mean` | double | `1.0` | 0.0 – 64.0 | Center of the normal aptitude distribution. `1.0` is neutral progression speed. Must lie within `minimum`…`maximum`. |
| `variance` | double | `0.09` | 0.0 – 64.0 | Distribution variance (default standard deviation 0.3). `0.0` makes every generated aptitude equal `mean`. |
| `minimum` | double | `0.5` | 0.0 – 64.0 | Lower clamp on generated aptitudes; must be below `maximum`. |
| `maximum` | double | `2.0` | 0.0 – 64.0 | Upper clamp, including rare-talent draws. |

Generation draws a bounded Gaussian sample per profession; samples outside the
bounds are redrawn (up to 100 attempts) and finally clamped.

### `[aptitude.rareTalents]`

| Option | Type | Default | Range | Effect |
| --- | --- | --- | --- | --- |
| `enabled` | bool | `true` | — | Allows the exceptional upper-tail draw; `false` leaves ordinary generation unchanged. |
| `chance` | double | `0.02` | 0.0 – 1.0 | Chance that one generated profession's aptitude is a rare talent. |
| `strength` | double | `3.0` | 0.0 – 10.0 | Minimum rare-talent offset above the mean, in distribution standard deviations. |

Rare talents raise one aptitude by at least `strength` standard deviations
(a half-normal draw above that), then apply the same clamps.

## `[inheritance]` — genetics of bred children

| Option | Type | Default | Range | Effect |
| --- | --- | --- | --- | --- |
| `enabled` | bool | `true` | — | Children derive aptitudes from their parents. `false` gives children freshly generated values. |
| `inheritanceStrength` | double | `0.7` | 0.0 – 1.0 | Weight of the two parents' average aptitude in each child aptitude. |
| `randomContribution` | double | `0.2` | 0.0 – 1.0 | Weight of a fresh generation draw (a full draw, rare-talent roll included, scaled by this weight). `inheritanceStrength + randomContribution` must not exceed `1.0`. |
| `mutationChance` | double | `1.0` | 0.0 – 1.0 | Chance that an inherited aptitude receives a zero-mean mutation. |
| `mutationVariance` | double | `0.01` | 0.0 – 64.0 | Variance of the mutation (default standard deviation 0.1). |

Any leftover weight (with defaults, `1 − 0.7 − 0.2 = 0.1`) is weighted toward
the configured generation mean as a neutral anchor. A parent missing an
aptitude contributes the generation mean. Children inherit aptitudes only:
careers, skill, learned trades, and demand always start empty.

## `[career]` — what counts as work time

| Option | Type | Default | Range | Effect |
| --- | --- | --- | --- | --- |
| `enabled` | bool | `true` | — | Counts eligible loaded server ticks as professional tenure. `false` freezes accumulation but preserves all recorded history. Valid non-nitwit employment is always required. |
| `adultsOnly` | bool | `true` | — | Requires adulthood for a loaded profession tick to count. |
| `requireJobSite` | bool | `false` | — | Requires a remembered, valid job site for a tick to count. |
| `requireWorkActivity` | bool | `false` | — | Requires the villager's current brain activity to be `WORK` for a tick to count. |

Tenure accumulates in small batches while villagers are loaded and is flushed
to disk roughly every 20 eligible ticks, when a villager unloads, and before
zombie conversion.

## `[skill]` — time-based professional skill

| Option | Type | Default | Range | Effect |
| --- | --- | --- | --- | --- |
| `enabled` | bool | `true` | — | Converts eligible tenure into persistent skill. `false` stops gains and preserves skill already earned. |
| `baseProgressionRate` | double | `0.001` | 0.0 – 1.0 | Skill gained per eligible server tick at neutral aptitude and activity. With defaults, one skill point takes 1000 eligible ticks (50 seconds of game time). |
| `aptitudeInfluence` | double | `1.0` | 0.0 – 1.0 | Fraction of the aptitude multiplier applied to skill gain; `0.0` makes everyone progress at the base rate, `1.0` applies aptitude fully. |
| `minimum` | double | `0.0` | 0.0 – 1 000 000.0 | Lower skill bound. Existing stored history is never reset or lowered on reload. |
| `maximum` | double | `1.0` | 0.0 – 1 000 000.0 | Cap on gained skill; lowering it never reduces already-earned skill. |

Gain formula per batch:
`elapsed eligible ticks × baseProgressionRate × (1 + (aptitude − 1) × aptitudeInfluence) × activity multiplier`,
clamped to `maximum`. Skill is persistent state; trades never add skill
directly.

## `[levels]` — vanilla profession levels from skill

| Option | Default | Range |
| --- | --- | --- |
| `novice` | `0.0` | 0.0 – 1 000 000.0 |
| `apprentice` | `0.2` | 0.0 – 1 000 000.0 |
| `journeyman` | `0.5` | 0.0 – 1 000 000.0 |
| `expert` | `0.8` | 0.0 – 1 000 000.0 |
| `master` | `1.0` | 0.0 – 1 000 000.0 |

All five are inclusive skill thresholds and must be strictly increasing, with
`novice` at or above `skill.minimum` and `master` at or below `skill.maximum`.
Reaching a threshold grants the corresponding vanilla profession level
(Novice = 1 … Master = 5); unlocking a level regenerates offers exactly like
vanilla. Villagers that already had a higher vanilla level when the mod was
installed bootstrap skill to that level's threshold instead of being demoted.
With defaults, master requires 1.0 skill — about 16 minutes of eligible
employment at neutral speed.

## `[activity]` — trading as accelerator

| Option | Type | Default | Range | Effect |
| --- | --- | --- | --- | --- |
| `enabled` | bool | `true` | — | Successful trades accelerate progression. `false` uses a neutral `1.0` multiplier and preserves old activity history. |
| `gainPerSuccessfulTrade` | double | `0.1` | 0.0 – 64.0 | Multiplier added per successful trade. `0.0` disables acceleration. |
| `decayRate` | double | `0.0001` | 0.0 – 64.0 | Multiplier movement toward `baseline`, per loaded server tick. `0.0` disables decay. |
| `baseline` | double | `1.0` | 0.5 – 64.0 | Neutral multiplier the score relaxes toward. `1.0` leaves the base rate unchanged. |
| `maximumMultiplier` | double | `2.0` | 0.01 – 64.0 | Cap on the trade-activity multiplier; must be at least `baseline`. |

Each successful trade with any villager raises that profession's activity
score (after applying decay since the last update), bounded by
`maximumMultiplier`. Activity multiplies skill gain from elapsed time; it adds
no skill by itself and cannot schedule levels.

## `[specializations]` — professional specialization

Specialization assignments come from data pack definitions
(`data/<namespace>/villager_potential_specializations/...`; see
[specialization-definitions.md](specialization-definitions.md)). These options
control strength, not contents.

| Option | Type | Default | Range | Effect |
| --- | --- | --- | --- | --- |
| `enabled` | bool | `true` | — | Applies datapack-defined weights. `false` keeps stored assignments but renders all weights neutral. |
| `globalStrength` | double | `1.0` | 0.0 – 1.0 | Global specialization strength: `0.0` neutral, `1.0` full configured bias. |
| `minimumBias` | double | `0.1` | 0.0 – 1.0 | Bias blend expressed at minimum professional skill. |
| `maximumBias` | double | `1.0` | 0.0 – 1.0 | Bias blend expressed at maximum professional skill; must be at least `minimumBias`. |
| `curveExponent` | double | `2.0` | 0.01 – 100.0 | Exponent shaping how quickly bias strengthens with skill; larger values delay strong specialization. |
| `professionStrengthOverrides` | string list | `[]` | each `id=value`, value 0.0 – 1.0 | Per-profession strength overrides, e.g. `"minecraft:librarian=0.75"`. They do not replace datapack definitions. |

Effective bias interpolates from `minimumBias` toward `maximumBias` as skill
moves from `skill.minimum` to `skill.maximum`, scaled by the profession's
strength (override if present, else `globalStrength`) and shaped by
`curveExponent`. Category modifiers multiply the vanilla candidate weights
during offer generation; they can suppress a category at full strength but can
never add candidates vanilla does not offer. A villager stores its assigned
specialization when it first enters a profession.

## `[palette]` — learned-trade policy

| Option | Type | Default | Effect |
| --- | --- | --- | --- |
| `mode` | enum | `PERSISTENT` | How remembered logical trades affect offer regeneration: `PERSISTENT`, `VANILLA`, `WEIGHTED_MEMORY`, `EXHAUST`, or `CYCLIC`. Mode changes never delete stored learned trades or history. |

- **PERSISTENT** — the default. Offers are generated once and then restored;
  leveling up only appends newly learned trades. Removing a workstation,
  losing a profession, or reloading never rerolls what the villager knows.
- **VANILLA** — vanilla-style rerolls every regeneration, while Villager
  Potential still records history and demand.
- **WEIGHTED_MEMORY** — previously presented trades keep reduced weight,
  recovering back to full as eligible profession time passes (see
  `[memory]`).
- **EXHAUST** — a recently presented trade is unavailable until it has been
  idle for `exhaustionRecoveryTicks` of eligible profession time.
- **CYCLIC** — each candidate appears once per cycle; a new cycle starts after
  every candidate has been idle for `cycleRecoveryTicks`.

In all non-persistent modes the pool still comes from vanilla's candidate
array; memory only reorders or suppresses candidates. If no candidate is
eligible, selection falls back safely rather than producing empty offers.

## `[memory]` — trade memory tuning

| Option | Type | Default | Range | Effect |
| --- | --- | --- | --- | --- |
| `repeatedTradePenalty` | double | `0.75` | 0.0 – 1.0 | Weight penalty applied to already-seen trades (`WEIGHTED_MEMORY` only): `0.0` none, `1.0` reduces a seen trade to its floor immediately. |
| `minimumCandidateWeight` | double | `0.01` | ≥ 0.0 | Absolute weight floor while a repeated-trade penalty recovers. |
| `decayTicks` | long | `24000` | ≥ 1 | Eligible profession ticks until a `WEIGHTED_MEMORY` penalty fully decays (24000 ticks = one Minecraft day). Recovery is linear in elapsed profession time. |
| `rareTradeProtectionEnabled` | bool | `false` | — | Gives configured rare results the shorter recovery window below. `false` leaves their history unchanged and unprotected. |
| `rareTradeRecoveryTicks` | long | `0` | ≥ 0 | Rare-result recovery in eligible profession ticks; `0` disables shortened recovery. |
| `rareTradeResultItems` | string list | `[]` | namespaced item IDs | Result items eligible for rare-trade protection, e.g. `"minecraft:mending"`-style enchanted book results are matched by result item ID. |
| `exhaustionRecoveryTicks` | long | `24000` | ≥ 1 | Eligible profession ticks before an exhausted candidate may return (`EXHAUST`). |
| `cycleRecoveryTicks` | long | `24000` | ≥ 1 | Eligible profession ticks all candidates must stay unseen before a `CYCLIC` palette resets. |

History capacity per profession is 128 observed logical trades (an internal
default, not currently exposed as an option); the least recently observed
entries fall out first. Memory timers count eligible profession time for
`WEIGHTED_MEMORY`, `EXHAUST`, and `CYCLIC`, so villagers only "recover" while
actually employed.

## `[economy.demand]` — per-trade demand scores

| Option | Type | Default | Range | Effect |
| --- | --- | --- | --- | --- |
| `enabled` | bool | `true` | — | Records demand from successful uses. `false` preserves stored demand but neither updates nor applies it. |
| `gainPerSuccessfulUse` | double | `1.0` | > 0 | Demand score gained per successful trade use. |
| `decayPerTick` | double | `0.000833` (`1/1200`) | ≥ 0.0 | Score movement toward `baseline` per loaded server tick; `0.0` disables decay. One purchase's demand decays in about 1200 ticks (60 s). |
| `minimum` | double | `0.0` | finite | Lower bound for each logical trade's score. |
| `baseline` | double | `0.0` | finite, within `minimum`…`maximum` | Neutral score approached during decay. |
| `maximum` | double | `100.0` | finite, ≥ `baseline` | Upper bound for each score. |

Demand is tracked per villager, per profession, per stable logical trade
(identity comes from base cost/result items and components, independent of
current discounts).

### `[economy.price]` — demand-based pricing

| Option | Type | Default | Range | Effect |
| --- | --- | --- | --- | --- |
| `enabled` | bool | `true` | — | Applies demand to prices independently of stock influence; `false` preserves vanilla-adjusted prices. |
| `minimumMultiplier` | double | `1.0` | 0.01 – 1.0 | Price multiplier applied at `demand.minimum`; the baseline stays neutral at 1.0. Values below `1.0` make unwanted trades cheaper than vanilla. |
| `maximumMultiplier` | double | `2.0` | 1.0 – 64.0 | Price multiplier applied at `demand.maximum`; item stack limits still apply. |

The multiplier interpolates linearly between the demand bounds through the
neutral baseline and is applied as a bounded delta on top of vanilla's own
adjusted price, so hero-of-the-village and vanilla demand effects remain
intact.

### `[economy.stock]` — optional stock effects

| Option | Type | Default | Range | Effect |
| --- | --- | --- | --- | --- |
| `enabled` | bool | `false` | — | Lets high demand raise an offer's use ceiling. Applied only after a genuine vanilla-approved restock; never creates extra restocks or bypasses workstation and daily timing checks. |
| `influenceStrength` | double | `1.0` | 0.0 – 1.0 | Scales how much of the additional-use budget demand may use. |
| `maximumAdditionalUses` | int | `2` | 0 – 64 | Hard cap on uses demand may add to one offer per restock; `0` neutralizes the feature. |
| `maximumUsesPerOffer` | int | `16` | 1 – 64 | Absolute total-use ceiling; offers already above it are never reduced. |

Additional uses grow linearly with demand above the baseline up to
`maximumAdditionalUses × influenceStrength`, always limited by the remaining
room under `maximumUsesPerOffer`. Demand at or below the baseline is neutral.

## `[playerFeedback]`

| Option | Type | Default | Effect |
| --- | --- | --- | --- |
| `aptitudeDisplay` | enum | `DISABLED` | Optional hint shown in the action bar when a player interacts (main hand) with a villager that has a current non-nitwit profession. `DISABLED` sends nothing; `QUALITATIVE` shows the tier word; `EXACT` additionally shows the stored number and is an explicit server-owner opt-in. |

Tiers are derived from the configured `[aptitude]` distribution: boundaries sit
at mean −1σ and mean +1σ/+2σ (clamped into `[minimum, maximum]`), with the
`Exceptional` boundary at `max(2.0, rareTalentStrength)` standard deviations
above the mean. With defaults this yields roughly `Poor < 0.7 ≤ Average <
1.3 ≤ Promising < 1.6 ≤ Talented < 1.9 ≤ Exceptional`.

## `[debug]`

| Option | Type | Default | Effect |
| --- | --- | --- | --- |
| `enabled` | bool | `false` | Logs concise semantic diagnostics (initialization, migration, inheritance, profession changes, specializations, learning, trade decisions, demand and price adjustments) prefixed `[Villager Potential/debug]`. Never logs per-tick progression or full state dumps. |
| `detailedTradeWeights` | bool | `false` | Additionally logs each resolved trade candidate weight. Requires `debug.enabled`; can be noisy during trade generation. |

---

## Important interactions

**Aptitude vs. skill.** Aptitude is innate and immutable; skill is earned and
persistent. Aptitude affects only the speed of skill gain, and only as far as
`skill.aptitudeInfluence` allows. Level thresholds are absolute skill values,
so a talented villager reaches Journeyman sooner but the thresholds themselves
never move. Disabling `[skill].enabled` freezes gains but keeps earned skill;
disabling `[aptitude].enabled` does not erase anyone's aptitudes.

**Activity vs. demand.** Both react to successful trades but feed different
systems. Activity is per profession, accelerates skill gain, and decays toward
its baseline; demand is per logical trade, drives price (and optionally stock),
and decays separately. Either can be disabled alone; disabling one never blocks
the other, and neither grants experience or levels directly.

**Specialization vs. palette.** Specialization weights decide how attractive a
candidate category is while offers are being generated; the palette policy
decides whether regeneration restores learned trades or rolls anew. They
compose in a fixed order — vanilla weight × specialization bias × trade memory
× config override × integration listeners — so a librarian biased toward
enchanted books still respects whatever `palette.mode` says about rerolling.
Disabling specializations neutralizes weighting but keeps stored assignments;
switching palette modes never deletes learned trades.

**PERSISTENT vs. reroll modes.** Only `PERSISTENT` reconstructs previously
learned offers. `VANILLA` ignores memory entirely; `WEIGHTED_MEMORY`,
`EXHAUST`, and `CYCLIC` progressively restrict repeats using the recovery
timers above. All five modes record identical history, so switching modes later
is safe and reversible: stored knowledge is never destroyed by the mode switch
itself, and explicit destruction exists only through admin commands
(`/villagerpotential regenerate ...`).

## Validation summary

Reload fails (previous config kept) when any of these are violated:

- `aptitude.minimum < aptitude.maximum`, `mean` inside `[minimum, maximum]`;
- `inheritance.inheritanceStrength + inheritance.randomContribution ≤ 1.0`;
- `skill.minimum < skill.maximum`;
- level thresholds strictly increasing and inside `[skill.minimum, skill.maximum]`;
- `activity.maximumMultiplier ≥ activity.baseline`;
- `specializations.minimumBias ≤ specializations.maximumBias`;
- `economy.demand.minimum ≤ baseline ≤ maximum`, `gainPerSuccessfulUse > 0`;
- `economy.price.minimumMultiplier ≤ 1.0 ≤ economy.price.maximumMultiplier`;
- list entries parse as namespaced IDs (or `id=value` overrides with unique IDs).
