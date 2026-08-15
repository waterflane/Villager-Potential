# Villager lifecycle in Minecraft 1.21.1 / NeoForge 21.1

This note is limited to lifecycle paths that matter to persistent Villager Potential data. It was verified against this workspace's generated `neoforge-21.1.248-sources.jar` and Minecraft resources artifact (Minecraft 1.21.1, NeoForge 21.1.248).

## Creation and entry into the level

| Route | Relevant 1.21.1 path | Important result |
| --- | --- | --- |
| Ordinary construction | `EntityType.VILLAGER` uses `Villager::new`; `Villager(EntityType, Level)` starts with plains type, profession `NONE`, and level 1. A caller using only `EntityType#create(Level)` does not run spawn finalization. | Do not use the constructor or `finalizeSpawn` as the sole initialization hook. Programmatic callers can bypass finalization but must still add the entity to a level. |
| Natural mob spawning | `EntityType.VILLAGER` is `MobCategory.MISC`; `NaturalSpawner#isValidSpawnPostitionForType` rejects `MISC`, so vanilla `Villager` is not naturally mob-spawned. `ZombieVillager` is a monster spawn in `BiomeDefaultFeatures#monsters`; `NaturalSpawner#spawnCategoryForPosition` creates it, calls `finalizeSpawn(..., NATURAL, ...)`, then adds it. | A naturally spawned zombie villager is not evidence that it previously held villager data. It should acquire new Potential if it is later cured unless it already has transferred/persisted Potential. |
| Village world generation | Village resident templates exist under `data/minecraft/structure/village/<type>/villagers/*.nbt`. `SinglePoolElement#place` uses settings with entities enabled and `setFinalizeEntities(true)`. `StructureTemplate#placeInWorld` -> `addEntitiesToWorld` loads entity NBT with `EntityType#create(CompoundTag, Level)`, calls `finalizeSpawn(..., STRUCTURE, ...)`, then `addFreshEntityWithPassengers`. `Villager#finalizeSpawn` sets `assignProfessionWhenSpawned`. | World-generated villagers enter through the same level-add boundary as other new villagers. When the generated chunk is promoted, `PersistentEntitySectionManager#addWorldGenChunkEntities` posts `EntityJoinLevelEvent` with `loadedFromDisk == false`. |
| `/summon` | `SummonCommand#createEntity` uses `EntityType#loadEntityRecursive`, then adds with `ServerLevel#tryAddFreshEntityWithPassengers`. The no-NBT and position-only forms call `finalizeSpawn(..., COMMAND, ...)`; the explicit-NBT form deliberately does **not** finalize. | `FinalizeSpawnEvent` cannot cover every summon. `EntityJoinLevelEvent` covers both forms after any supplied NBT has been read. |
| Spawn egg placed in the world | Both `SpawnEggItem#useOn` and the liquid-target branch of `SpawnEggItem#use` call `EntityType#spawn(..., SPAWN_EGG, ...)`. `EntityType#create(ServerLevel, ..., MobSpawnType, ...)` constructs, positions, finalizes, applies item entity data, and the spawn method adds it. | The ordinary egg path reaches `Villager#finalizeSpawn(SPAWN_EGG)` and then `EntityJoinLevelEvent`. |
| Matching spawn egg used on a villager | `Mob#checkAndHandleImportantInteractions` -> `SpawnEggItem#spawnOffspringFromSpawnEgg`. Because a villager is an `AgeableMob`, this calls `Villager#getBreedOffspring`, whose child is finalized as `BREEDING`, marked as a baby, and added. | This egg path is intentionally indistinguishable from breeding by `MobSpawnType`; level entry is the common hook. |
| Villager breeding | `VillagerMakeLove#breed` calls `Villager#getBreedOffspring`; that constructor chooses the child's villager type, calls `finalizeSpawn(..., BREEDING, ...)`, then `VillagerMakeLove` sets the baby age and calls `addFreshEntityWithPassengers`. `Villager#finalizeSpawn` forces profession `NONE`. | `BabyEntitySpawnEvent` is **not** fired: its 1.21.1 call sites are `Animal#spawnChildFromBreeding` and `Fox#spawnChildFromBreeding`, while villagers use `VillagerMakeLove`. Use level entry, not `BabyEntitySpawnEvent`, for complete coverage. |
| Entity load from disk | `EntityStorage#loadEntities` -> `EntityType#loadEntitiesRecursive` constructs each entity and calls `Entity#load` before it is queued. `PersistentEntitySectionManager#processPendingLoads` -> `addEntity(entity, true)` posts `EntityJoinLevelEvent(entity, level, true)`. The legacy chunk path also uses `true`. | `EntityJoinLevelEvent#loadedFromDisk()` is the reliable discriminator for persisted entities on the logical server. No spawn finalization occurs on disk load. |

`EntityJoinLevelEvent` is posted on both logical sides and is cancellable. All Potential work there must be server-side. It is posted before the entity is accepted into the manager, so the handler should not perform unrelated world/chunk access; the event documentation also warns that a joining entity's chunk may not yet be `FULL`.

## Profession assignment and removal

The normal adult AI installs both relevant behaviors in `VillagerGoalPackages#getCorePackage`:

- `ValidateNearbyPoi#create` validates the remembered `JOB_SITE` when the villager is within 16 blocks and erases that memory if the matching POI no longer exists. This is the usual precursor to profession removal.
- `AssignProfessionFromJobSite#create` requires `POTENTIAL_JOB_SITE`. Once close enough (or during the one-tick structure-spawn allowance), it moves that memory to `JOB_SITE`. If the current profession is `NONE`, it finds the profession whose held-job-site predicate matches the POI, calls `Villager#setVillagerData(old.setProfession(...))`, and refreshes the brain.
- `ResetProfession#create` runs when `JOB_SITE` is absent. It changes the profession to `NONE` only when the current profession is neither `NONE` nor `NITWIT`, villager XP is zero, and villager level is at most 1; it then refreshes the brain.

`Villager#setVillagerData` is the common live setter and clears cached offers when the profession changes. NBT load is different: `Villager#readAdditionalSaveData` decodes `VillagerData` directly into synced entity data, so loading an existing profession is not a live assignment. Structure/summon NBT and conversion can also supply `VillagerData`.

There is no dedicated NeoForge profession-change event in 21.1.248. `VillagerTradesEvent` concerns trade registration, not a villager changing profession. If a later feature must react to every live profession transition, the important vanilla boundary is `Villager#setVillagerData`; hooking only `AssignProfessionFromJobSite` or `ResetProfession` would miss commands and conversions.

## Villager to zombie villager

The path is `Zombie#killedEntity` on Normal or Hard difficulty:

1. NeoForge calls `EventHooks#canLivingConvert(villager, EntityType.ZOMBIE_VILLAGER, ...)`, which posts `LivingConversionEvent.Pre`.
2. If conversion is selected, `villager.convertTo(EntityType.ZOMBIE_VILLAGER, false)` calls `Mob#convertTo`. That method constructs the replacement, copies basic mob state, adds the replacement to the level, and discards the villager.
3. `Zombie#killedEntity` finalizes the replacement with `MobSpawnType.CONVERSION`, then explicitly copies `VillagerData`, gossips, offers, and villager XP.
4. `EventHooks#onLivingConvert(source, outcome)` posts `LivingConversionEvent.Post` with the finalized pair.

The replacement's `EntityJoinLevelEvent` occurs inside `Mob#convertTo`, before the explicit villager fields are copied and before `LivingConversionEvent.Post`. It is therefore a good existence/initialization boundary but not a safe source-to-outcome transfer boundary.

## Zombie villager curing

`ZombieVillager#mobInteract` starts curing after a weakened zombie villager consumes a golden apple. The timer is saved by `ZombieVillager#addAdditionalSaveData` and restored by `readAdditionalSaveData`. On timer completion:

1. `ZombieVillager#tick` posts `LivingConversionEvent.Pre` through `EventHooks#canLivingConvert(..., EntityType.VILLAGER, ...)`.
2. `ZombieVillager#finishConversion` calls `convertTo(EntityType.VILLAGER, false)`, which adds the new villager and discards the zombie villager.
3. It restores equipment, `VillagerData`, gossips, offers, and XP; calls `Villager#finalizeSpawn(..., CONVERSION, ...)`; refreshes the brain; and applies cure effects/reputation.
4. It finally posts `LivingConversionEvent.Post` with the zombie villager source and restored villager outcome.

As in zombification, the outcome joins the level before its villager-specific state is restored. `LivingConversionEvent.Post` is the first NeoForge event that exposes both finalized entities.

## Recommended Potential integration

Use one registered NeoForge `AttachmentType<PotentialData>` (or the final scalar type) as the storage primitive. Data attachments are preferable to an entity capability here: a serializer gives them built-in entity NBT persistence, lazy defaults, and conversion copying. Register the type in `NeoForgeRegistries.Keys.ATTACHMENT_TYPES`.

1. **Initialize new villagers at level entry.** On the logical server, handle `EntityJoinLevelEvent` for `Villager` with `loadedFromDisk() == false` and materialize the attachment through the mod's single Potential accessor. This covers world generation, both summon forms, both spawn-egg forms, breeding, curing outcomes, and direct programmatic additions. Make the accessor idempotent (`hasData`/`getExistingDataOrNull` before generation, or rely on `getData`'s one-time default insertion).

   Level entry can precede AI profession assignment for structure villagers. Initial Potential generation at this boundary must not assume that `getVillagerData().getProfession()` is the villager's eventual profession.

2. **Initialize old-world villagers lazily.** For `loadedFromDisk() == true`, do not eagerly generate in the join handler. `Entity#load` has already deserialized any saved attachment. The same server-side Potential accessor should call `getData(attachmentType)` only when gameplay first needs Potential; `AttachmentHolder#getData` creates and stores the default only when no serialized value exists. This preserves existing values and upgrades attachment-less villagers on demand.

3. **Persist through the attachment serializer.** Build the attachment with `AttachmentType.Builder#serialize(Codec)` (or an `IAttachmentSerializer`) so `Entity#saveWithoutId` writes it beneath `neoforge:attachments` and `Entity#load` restores it before `Villager#readAdditionalSaveData`. Entity attachments do not require the block-entity/chunk `setChanged` calls. Client synchronization is separate and should only be enabled if UI/gameplay later needs the value client-side.

4. **Transfer across both conversions through NeoForge conversion support.** Mark the serializable attachment with `AttachmentType.Builder#copyOnDeath()`. Despite the name, its 1.21.1 contract explicitly includes living conversion. NeoForge's `AttachmentInternals#onLivingConvert` listens to `LivingConversionEvent.Post` and calls `outcome.copyAttachmentsFrom(source, true)`, copying opted-in attachments with their copy handler. If Potential must never attach to other conversion outcomes, supply a copy handler that returns a copy only when the target holder is a `Villager` or `ZombieVillager`.

There is one lazy-upgrade edge case: NeoForge copies only attachments already present in the source attachment map. Add a narrowly filtered, server-side `LivingConversionEvent.Pre` handler for exactly `Villager -> ZOMBIE_VILLAGER` and `ZombieVillager -> VILLAGER`, and invoke the same Potential accessor on the source. This materializes a value for an old-world entity immediately before conversion; the built-in `Post` handler then transfers it. Do not copy at `EntityJoinLevelEvent`, because the outcome has already joined before the source/outcome relationship and finalized data are available.

## Implementation checklist for later prompts

- Register a serializable Potential attachment in the attachment-type registry.
- Keep generation in one idempotent, logical-server-only accessor.
- Materialize on new, non-disk `Villager` joins; leave disk-loaded villagers lazy.
- Materialize the source in `LivingConversionEvent.Pre` for only the two villager conversion pairs.
- Opt into NeoForge conversion copy with `copyOnDeath()` and, if needed, a target-filtering copy handler.
- Do not depend solely on `finalizeSpawn`, `MobSpawnType`, `BabyEntitySpawnEvent`, or a profession behavior for lifecycle coverage.
