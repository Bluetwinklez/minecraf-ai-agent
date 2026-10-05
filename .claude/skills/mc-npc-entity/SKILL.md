---
name: mc-npc-entity
description: Create a custom NPC entity in a Minecraft mod - EntityType registration, attributes, model/renderer, skins, spawn eggs, spawning, and saving NPC state. Use whenever adding or changing an NPC mob.
---

# NPC entity

## Base class
- Humanoid, pathfinding NPC: extend `PathfinderMob` (not `Monster` unless hostile).
- Villager-like trading: `AbstractVillager` or implement `Merchant` yourself.
- Player-skin NPC: `PathfinderMob` + `HumanoidModel`/`PlayerModel` renderer.

## Checklist
1. Register `EntityType` (`MobCategory.CREATURE` or `MISC`), size ~`0.6F x 1.95F`.
2. Register attributes: Fabric `FabricDefaultAttributeRegistry.register`, (Neo)Forge `EntityAttributeCreationEvent`. Missing attributes -> crash on spawn.
3. Client: register renderer + `ModelLayerLocation` (client entrypoint / `EntityRenderersEvent`). Texture at `assets/<modid>/textures/entity/<name>.png`.
4. Spawn egg item + lang entry `entity.<modid>.<name>`.
5. Natural spawning (optional): biome modifier (NeoForge) / `BiomeModifications` (Fabric) + spawn placement.
6. Persistence: override `addAdditionalSaveData` / `readAdditionalSaveData`; call `setPersistenceRequired()` so NPC does not despawn.
7. Synced fields (name, profession, skin, mood): `SynchedEntityData` with `EntityDataAccessor`, defined in `defineSynchedData`.

## Pitfalls
- Never touch renderer/model classes from common code.
- `removeWhenFarAway` must return `false` for named/story NPCs.
- Custom name: `setCustomName` + `setCustomNameVisible(true)`.
