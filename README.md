# AI Builders for Minecraft

A Minecraft builder-NPC project. **The default `main` branch is a project entry point; the implementation is currently in [pull request #1](https://github.com/Bluetwinklez/minecraf-ai-agent/pull/1), branch `claude/sa-0h4r62`.** Cloning `main` alone does not install the mod.

## Implementation branch

The PR describes a Fabric mod for Minecraft 26.2/26.3 with Java 25: owner-controlled builder NPCs, schematic loading, material collection, construction progress, pause/resume and optional natural-language tools through Ollama or Claude.

Review and check out that branch to inspect its build instructions and Gradle configuration. Keep the Minecraft version, loader and Java version aligned; do not infer compatibility with other releases.

## Validation limits

The PR records 32 JUnit tests and 9 headless GameTests, and successful branch build runs are visible in Actions. These are historical branch results, not a new validation run of this documentation change. Real-client rendering and live Ollama/Claude replies were not verified in that work. No model credential is required for reading this repository.

Before using the mod in a world you value, test it in a disposable world, back up your saves and review the NPC ownership and modification permissions. Never commit provider keys, account sessions, personal worlds or server credentials.

## Repository status

The implementation PR remains separate so its code can be reviewed without silently moving experimental work into `main`. The default branch now provides accurate English navigation and automated secret scanning.

[Security policy](SECURITY.md) · [Repository security audit](SECURITY_AUDIT.md)
