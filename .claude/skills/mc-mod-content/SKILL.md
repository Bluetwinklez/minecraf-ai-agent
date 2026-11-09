---
name: mc-mod-content
description: Add content to a Minecraft mod - items, blocks, entities, recipes, loot tables, tags, lang files, models, and data generation.
---

# Adding mod content

## Registration
- Register everything through the loader's registry system (Fabric `Registry.register`; NeoForge/Forge `DeferredRegister`). Never construct registry objects statically outside it.
- Create ids with the mod namespace: `ResourceLocation`/`Identifier` of `modid:name`.

## Per feature, touch all of these
| Feature | Code | Assets (`assets/<modid>`) | Data (`data/<modid>`) |
|---|---|---|---|
| Item | Item class + registration, creative tab | `models/item/*.json`, `lang/en_us.json` | recipes, tags |
| Block | Block + BlockItem | `blockstates/`, `models/block/`, `textures/block/` | `loot_table/blocks/`, mineable/tool tags |
| Entity | EntityType, attributes, renderer (client) | model, texture, lang | spawn/loot |

## Rules
- Prefer **data generation** (`runData`/`datagen`) over hand-written JSON for models, lang, recipes, loot and tags.
- Block without a loot table drops nothing; block without a mineable tag cannot be broken with the right tool.
- Folder names changed in 1.21 (`recipe`, `loot_table` singular); check the target Minecraft version.
- Add a lang entry for every new item/block/entity/tab.
