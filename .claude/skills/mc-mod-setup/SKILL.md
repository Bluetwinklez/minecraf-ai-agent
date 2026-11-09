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
