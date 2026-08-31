# Server configuration reference

Villager Potential registers one Forge/NeoForge server config,
`villager_potential-server.toml`. Durations use the Minecraft clock: 20 ticks
per second, 1,200 per minute, and 24,000 per full day. Reloaded values are
validated together; invalid cross-field combinations are rejected instead of
being partially activated.

This is a world-owned `SERVER` config, so Forge does not put it in the global
`config` directory. It is created after the world is opened at
`saves/<world>/serverconfig/villager_potential-server.toml` in singleplayer, or
`<world>/serverconfig/villager_potential-server.toml` on a dedicated server.

`career.requireJobSite` and `playerFeedback.aptitudeDisplay` were removed from
the active schema. A valid owned workstation is mandatory, and interaction
messages no longer exist. Old files may retain these keys until the loader
rewrites them, but they have no runtime effect.

## Aptitude and inheritance

| Key | Default | Meaning |
| --- | ---: | --- |
| `aptitude.enabled` | `true` | Generates stable per-profession aptitudes; when disabled, new values are neutral `1.0` and existing values remain. |
| `aptitude.mean` | `1.0` | Center of the aptitude distribution; `1.0` is neutral learning speed. |
| `aptitude.variance` | `0.09` | Distribution variance; its square root is the standard deviation. |
| `aptitude.minimum` | `0.5` | Lowest generated profession aptitude. |
| `aptitude.maximum` | `2.0` | Highest generated aptitude, including rare talents. |
| `rareTalents.enabled` | `true` | Enables an exceptional upper-tail aptitude roll. |
| `rareTalents.chance` | `0.02` | Probability that a new villager receives one rare profession talent. |
| `rareTalents.strength` | `3.0` | Minimum rare-talent offset in standard deviations. |
| `inheritance.enabled` | `true` | Children derive aptitudes from parents; disabling it generates fresh values. |
| `inheritance.inheritanceStrength` | `0.7` | Weight of the parents' average in each child aptitude. |
| `inheritance.randomContribution` | `0.2` | Fresh-random weight; together with inheritance strength it cannot exceed `1.0`. |
| `inheritance.mutationChance` | `1.0` | Probability that an inherited aptitude receives a mutation. |
| `inheritance.mutationVariance` | `0.01` | Mutation variance; its square root is the mutation standard deviation. |

## Career and professional experience

Night ticks never count. A profession and valid owned workstation are always
required.

| Key | Default | Meaning |
| --- | ---: | --- |
| `career.enabled` | `true` | Counts eligible loaded ticks as tenure; disabling it preserves existing careers. |
| `career.adultsOnly` | `true` | Prevents babies accumulating tenure or experience. |
| `career.requireWorkActivity` | `false` | Counts only ticks during the villager brain's `WORK` activity when enabled. |
| `skill.enabled` | `true` | Converts eligible tenure into persistent experience; disabling it preserves earned experience. |
| `skill.baseProgressionRate` | `1 / 24000` | Experience per eligible tick before aptitude, purchases, and level-rate multipliers. |
| `skill.aptitudeInfluence` | `1.0` | `0.0` ignores individual aptitude; `1.0` applies it fully. |
| `skill.minimum` | `0.0` | Lower bound used when advancing experience. |
| `skill.maximum` | `10.5` | Experience cap; lowering it never deletes stored experience. |
| `levels.novice` | `0.0` | Inclusive threshold for level 1. |
| `levels.apprentice` | `1.5` | Inclusive threshold for level 2. |
| `levels.journeyman` | `3.5` | Inclusive threshold for level 3. |
| `levels.expert` | `6.5` | Inclusive threshold for level 4. |
| `levels.master` | `10.5` | Inclusive threshold for level 5; thresholds must be strictly increasing. |

The trade screen's individual learning multiplier is the current profession's
aptitude after `skill.aptitudeInfluence` is applied. The level bonus is displayed
separately because it is shared by all villagers at the same profession level.

## Purchase activity

| Key | Default | Meaning |
| --- | ---: | --- |
| `activity.enabled` | `true` | Successful purchases accelerate learning; disabling it uses neutral `1.0` and preserves history. |
| `activity.gainPerSuccessfulTrade` | `0.1` | Novice gain per completed trade; each new level requires 20% more trades, so the effective gain is divided by `1.2^(level - 1)`. |
| `activity.decayRate` | `0.0` | Removed per tick toward baseline; `0.0` keeps it until promotion. |
| `activity.baseline` | `1.0` | Neutral multiplier and reset value after a level-up. |
| `activity.maximumMultiplier` | `2.0` | Hard purchase-multiplier cap; it cannot be below baseline. |

## Specializations

| Key | Default | Meaning |
| --- | ---: | --- |
| `specializations.enabled` | `true` | Applies datapack specialization weights; stored assignments survive disabling. |
| `specializations.globalStrength` | `1.0` | Blend from neutral (`0.0`) to full configured bias (`1.0`). |
| `specializations.minimumBias` | `0.1` | Bias expressed at minimum experience. |
| `specializations.maximumBias` | `1.0` | Bias at maximum experience; cannot be below minimum bias. |
| `specializations.curveExponent` | `2.0` | Experience-to-bias curve; larger values delay strong specialization. |
| `specializations.professionStrengthOverrides` | `[]` | Optional `namespace:profession=value` strengths from `0.0` to `1.0`. |

## Trade palette and memory

Persistent history is internally capped at 128 logical trades per profession to
bound save size and restoration work.

| Key | Default | Meaning |
| --- | ---: | --- |
| `palette.mode` | `PERSISTENT` | `PERSISTENT`, `VANILLA`, `WEIGHTED_MEMORY`, `EXHAUST`, or `CYCLIC`; switching never deletes memory. |
| `memory.repeatedTradePenalty` | `0.75` | Maximum repeated-candidate penalty for `WEIGHTED_MEMORY`. |
| `memory.minimumCandidateWeight` | `0.01` | Absolute weight floor while a memory penalty is active. |
| `memory.decayTicks` | `24000` | Eligible ticks until a weighted penalty fully recovers. |
| `memory.rareTradeProtectionEnabled` | `false` | Enables shorter recovery for configured rare results. |
| `memory.rareTradeRecoveryTicks` | `0` | Rare-result recovery duration; `0` disables it. |
| `memory.rareTradeResultItems` | `[]` | Namespaced result item IDs treated as protected rare trades. |
| `memory.exhaustionRecoveryTicks` | `24000` | Eligible ticks before an exhausted candidate returns. |
| `memory.cycleRecoveryTicks` | `24000` | Idle eligible ticks before a completed cyclic pool resets. |

## Demand, prices, and stock

| Key | Default | Meaning |
| --- | ---: | --- |
| `economy.demand.enabled` | `true` | Records and applies per-villager, per-trade demand until completed sleep resets it; disabling preserves scores. |
| `economy.demand.gainPerSuccessfulUse` | `1.0` | Demand added per successful use. |
| `economy.demand.decayPerTick` | `1 / 1200` | Score moved toward baseline per tick; default is one point per minute. |
| `economy.demand.minimum` | `0.0` | Lower demand bound. |
| `economy.demand.baseline` | `0.0` | Neutral score approached by decay. |
| `economy.demand.maximum` | `100.0` | Upper demand bound. |
| `economy.price.enabled` | `true` | Enables demand price changes for non-emerald payment stacks. Set to `false` to leave payments unchanged by Villager Potential. |
| `economy.price.minimumMultiplier` | `1.0` | Demand-curve value at minimum demand; values at or below `1.0` never make a product cheaper. |
| `economy.price.maximumMultiplier` | `2.0` | Demand-curve value at maximum demand; the percentage setting below is the actual hard price cap. |
| `economy.price.maximumItemPaymentIncrease` | `0.125` | When paying another item, that payment stack may grow by at most 12.5%; an emerald result never changes. |
| `economy.price.demandScoreForMaximumPrice` | `8.0` | Demand points above baseline needed to reach the percentage cap; lower values make prices rise faster. |
| `economy.stock.enabled` | `false` | Lets demand add uses during the completed-sleep restock; never creates an additional restock. |
| `economy.stock.influenceStrength` | `1.0` | Blend from no stock effect to the full additional-use cap. |
| `economy.stock.maximumAdditionalUses` | `2` | Uses demand may add to one offer per restock. |
| `economy.stock.maximumUsesPerOffer` | `16` | Total-use ceiling for increases; larger existing offers are not reduced. |

Ordinary clicks recalculate and synchronize only the completed offer immediately
after its old-price purchase finishes, without requiring the player to close and
reopen the merchant screen. Other rows in the same trade window are untouched.
While demand pricing is enabled, emerald payments and all received result stacks
remain unchanged. A non-emerald payment cannot rise by more than the configured
increase from its base count. Once demand has raised such a payment, its displayed
item count cannot fall again before completed sleep, even if the underlying demand
score decays.

Shift-click has no special pricing rule. A successful result-slot action is
completed at the price it displayed, then that exact offer is recalculated once.
The former `economy.price.dynamicShiftPricing` and
`economy.price.maximumEmeraldPaymentResultReduction` keys are ignored and may
remain in an older TOML until Forge or NeoForge rewrites the file.

## Diagnostics

| Key | Default | Meaning |
| --- | ---: | --- |
| `debug.enabled` | `false` | Enables concise lifecycle, profession, trade, and demand server logs. |
| `debug.detailedTradeWeights` | `false` | Logs every candidate weight; requires debug and can be noisy. |

## Legacy migration

Both former built-in skill curves (`0.00005`/maximum `5.0` and
`0.001`/maximum `1.0`) migrate in memory to current defaults only when every
old default matches. Custom curves are never overwritten. Former default
activity decay `0.0001` similarly migrates to the current non-decaying value.
