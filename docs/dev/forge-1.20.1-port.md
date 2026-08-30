# Forge 1.20.1 port

Status: **implemented**. The `forge-1.20.1` module targets Minecraft 1.20.1,
Forge 47.4.23, Java 17, and declares the supported Forge range
`[47.4.10,48)`. The NeoForge 1.21.1 module remains Java 21; both consume the
same Java-17-compatible `core` classes and resources.

## Architecture

`core` owns aptitude, inheritance, progression, specialization, trade memory,
demand, schema migration, portable trade metadata, immutable API views, and
`TradeProgressSnapshot`. It has no Minecraft or loader imports. Platform
modules only translate Minecraft state, persist it, bind events/mixins,
serialize network messages, build `Component` tooltips, and render the vanilla
merchant screen overlay.

Forge stores state in an entity capability; NeoForge stores it in an
attachment. Both additionally write the same `villager_potential:data`
compound to entity NBT. Loading chooses the state with the greater schema, and
chooses the portable compound on an equal schema. Villager/zombie-villager
conversion explicitly materializes, flushes, and copies the state on Forge.

Schema 11 replaces version-specific item metadata strings with the `vp1:`
canonical representation. Schema-10 keys are migrated and colliding palette,
history, and demand entries are merged. Unknown metadata is marked unstable and
does not enter persistent palettes, history, or demand.

## Forge adapters

- Forge capability attachment for villagers and zombie villagers;
- entity join/leave/tick, trade, interaction, conversion, reload, command, and
  villager-trade events;
- ForgeConfigSpec with the same TOML keys, ranges, defaults, and atomic reload
  validation as NeoForge;
- standard Minecraft 1.20.1 trade pools (there is no trade-rebalance branch),
  with the same thirteen-profession seeded order and category names;
- 1.20.1 mixins for offer selection, trade-XP level suppression, inheritance,
  demand pricing/stock, restock, and portable entity NBT;
- SimpleChannel protocol 3 with one optional client-bound progress message;
- MerchantScreen progress bars and tooltips. The green bar uses the appropriate
  region of `textures/gui/container/villager2.png`; the blue bar is drawn
  directly. Client state is cleared when the screen closes.

The network layer is required for the optional UI and is present in both
platform modules. A dedicated server does not load client classes, and packet
sending checks that the remote endpoint has the channel.

## Verification

Use a Java 21 Gradle launcher; Gradle selects Java 17 for Forge/core and Java 21
for NeoForge:

```text
gradlew :core:check
gradlew :forge-1.20.1:check
gradlew :forge-1.20.1:runGameTestServer
gradlew :neoforge-1.21.1:check
gradlew :forge-1.20.1:release
```

Forge release verification checks loader metadata, mixin configuration, shared
translations, core classes, the entry point, and the absence of client imports
from common/server classes. GameTests cover progression, trade-XP suppression,
existing-villager bootstrap, capability/portable-NBT round trips, stable legacy
item metadata, and demand-stock mixin behavior.

World downgrade from Minecraft 1.21.1 to 1.20.1 is not supported. Only the
Villager Potential entity data is designed to transfer in both directions.
Vanilla trade metadata is portable; modded items also require matching
registrations and metadata understood on both versions.
