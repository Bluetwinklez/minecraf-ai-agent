---
name: mc-npc-builder
description: Work on the AI Builders builder NPC in this repo - .litematic/.nbt schematic reading, build ordering, survival materials and missing-material reports, scaffolding, /aib commands, GameTests. Use for any change under build/, entity/, command/ or chat/.
---

# AI Builders: builder NPC

Fabric 26.3 (Java 25, unobfuscated Mojang names: `Identifier`, `ValueInput`/`ValueOutput`, ...).
Check real signatures in the decompiled sources (`./gradlew genSources`, jar under
`.gradle/loom-cache/minecraftMaven/.../minecraft-common-*-sources.jar`) instead of guessing.

## Map
| Path | What |
|---|---|
| `build/schematic/LitematicReader` | `.litematic`: `Regions{name:{Position, Size (may be negative), BlockStatePalette, BlockStates long[]}}` |
| `build/schematic/PackedBitArray` | Litematica packing: `bits = max(2, ceil(log2(palette)))`, entries **span longs**, index `y*sx*sz + z*sx + x` |
| `build/schematic/PaletteFixer` | DataFixer `References.BLOCK_STATE` for old `MinecraftDataVersion`; unknown blocks -> air + counted |
| `build/BuildPlan` | world positions sorted: solid bottom-up, then non-solid, then fluids; rows snake |
| `build/MaterialResolver` | state -> item cost (upper door/bed half free, double slab = 2, candles/pickles/layers = count) |
| `build/BuildTask` | the job: pick target (lookahead prefers in-reach), walk, fetch from chests, wait + report, break obstructions, scaffold, place with `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE` |
| `entity/AgentNpc` | `PathfinderMob` + synced `ResolvableProfile`; client renders it through the vanilla player renderer via `AvatarRenderState` |
| `chat/ChatHandler` | `sa` -> `as`; `Claude ...` -> `ai/AgentLoop` (model + tools) on a single worker thread, offline -> `chat/IntentParser` |
| `command/BuilderActions` | Shared spawn/build/stop/pause logic + owner/op checks, used by `/aib` **and** the model's tools |
| `ai/ToolRegistry` | The only actions the model can take; arguments are untrusted, world access hops to the server thread |
| `chat/NameMatcher` | Fuzzy NPC/schematic names (case, Turkish letters, prefix, typos, folder file names) |

## Rules
- Never touch the world off the server thread. Schematic loading and LLM calls run on worker threads and hand results back with `server.execute`.
- Never `getBlockState` on unloaded chunks (`level.isLoaded(pos)` first) - it would load them.
- Every player-facing string is a lang key in both `en_us.json` and `tr_tr.json`.
- Missing materials: always list **every** missing item with counts (largest first), owner gets the full list, nearby players a one-liner; do not repeat an unchanged list.
- New player-facing actions go into `BuilderActions` first, then a command and (if safe) a tool. Never give the model an action the player could not run themselves; destructive ones (remove) stay command-only.
- Persist only what is needed to resume (schematic name, origin, rotation, mirror, mode, fingerprint); progress is recomputed from the world.

## Verify
- `./gradlew build` - compile + JUnit (`src/test`, pure Java).
- `./gradlew runGameTest` - headless server GameTests (`src/gametest`, arena structure 16x24x16). Add a GameTest for every behaviour change in `BuildTask`.
