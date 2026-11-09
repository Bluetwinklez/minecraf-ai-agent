---
name: mc-mod-build-test
description: Build, test, run, and package a Minecraft mod; diagnose crashes and Gradle failures. Use before declaring a mod change finished.
---

# Build, test, debug

## Commands
- `./gradlew build` - compile, tests, jar in `build/libs/`.
- `./gradlew runClient` / `runServer` - dev launch (server needs `run/eula.txt` with `eula=true`).
- `./gradlew runData` / `runDatagen` - regenerate generated resources; commit them.
- `./gradlew test` and game tests (`runGameTestServer`) for logic.

## Crash triage
1. Read `run/crash-reports/` and `run/logs/latest.log`; find the first `Caused by`.
2. `ClassNotFound`/`NoSuchMethod` on dedicated server: client-only class used in common code.
3. Missing texture/model (purple-black): path or namespace mismatch.
4. Registry/`null` errors: object used before registration or wrong registry order.
5. Mixin failure: see mc-mod-mixins skill.

## Before finishing
- Build passes; client and server both start for any change touching shared code.
- Version bumped in `gradle.properties` for releases; changelog updated.
