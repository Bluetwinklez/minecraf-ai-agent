---
name: mc-mod-setup
description: Set up or migrate a Minecraft mod project (Fabric, Forge, NeoForge). Use when starting a new mod, choosing a loader/Minecraft version, or fixing Gradle/mod metadata problems.
---

# Minecraft mod project setup

## Pick the loader
- **Fabric**: lightweight, fast updates, mixin-centric. `fabric.mod.json`, Fabric Loom plugin.
- **NeoForge**: modern Forge fork (1.20.2+). `META-INF/neoforge.mods.toml`, ModDevGradle/NeoGradle.
- **Forge**: legacy and older versions. `META-INF/mods.toml`, ForgeGradle.
- Multi-loader: use Architectury or a `common` + per-loader module layout.

Ask the user for the loader and Minecraft version if not stated; do not guess.

## Checklist
1. Match Java version to Minecraft (1.20.5+ / 1.21: Java 21; 1.18-1.20.4: Java 17).
2. Set `mod_id` (lowercase, `[a-z0-9_]`), `group`, `version`, `minecraft_version`, loader version in `gradle.properties`.
3. Keep `mod_id` identical across metadata file, resource namespaces and code constants.
4. Entry points: Fabric `ModInitializer`/`ClientModInitializer`; (Neo)Forge `@Mod("modid")`. Keep client-only code in client entrypoints/classes.
5. Add `.gitignore` for `.gradle/`, `build/`, `run/`, `.idea/`.
6. Verify with `./gradlew build` and `./gradlew runClient` (if a display is available) before finishing.

## Pitfalls
- Never reference client classes (`Minecraft`, renderers, screens) from common/server code.
- Use official Mojang mappings or Yarn consistently; do not mix.

## This repo's target: Fabric 26.2 + 26.3
Values checked against `meta.fabricmc.net` and `FabricMC/fabric-example-mod` (branch `26.3`) on 2026-10-05; re-check before bumping.

| | 26.2 | 26.3 |
|---|---|---|
| `minecraft_version` | `26.2` | `26.3` |
| `loader_version` | `0.19.5` | `0.19.5` |
| `fabric_api_version` | `0.161.0+26.2` | `0.161.0+26.3` |
| `loom_version` | `1.18-SNAPSHOT` | `1.18-SNAPSHOT` |
| Java | 25 | 25 |

26.x differences vs 1.21.x:
- Game ships **unobfuscated**: no Yarn/mappings line in `dependencies`; code uses Mojang names directly.
- Plugin id `net.fabricmc.fabric-loom`; deps use `implementation` (not `modImplementation`).
- `loom { splitEnvironmentSourceSets() }` -> client code in `src/client/java`, entrypoint `client` in `fabric.mod.json`.
- `fabric.mod.json` depends: `"minecraft": "~26.3"` (or `">=26.2 <26.4"` for one jar covering both), `"java": ">=25"`.

Two versions from one codebase:
- If APIs used are identical on both -> one jar, `minecraft` range `>=26.2 <26.4`, build against 26.2, test `runClient` on both.
- If they diverge -> multi-version setup (e.g. Stonecutter) with per-version `gradle.properties`; do not copy-paste source trees.
