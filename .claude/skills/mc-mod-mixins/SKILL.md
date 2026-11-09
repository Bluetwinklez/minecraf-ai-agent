---
name: mc-mod-mixins
description: Write and debug Mixins and loader events/hooks to change vanilla or other mods' behavior. Use for injecting, redirecting, or accessing vanilla internals.
---

# Mixins and hooks

## Order of preference
1. Loader events/APIs (Fabric API callbacks, NeoForge event bus) - safest.
2. Access wideners / access transformers for visibility.
3. Mixin: `@Inject`, `@ModifyVariable`, `@ModifyArg`, `@WrapOperation` (MixinExtras) before `@Redirect`; avoid `@Overwrite`.

## Rules
- Register in `<modid>.mixins.json`; split `mixins` (common), `client`, `server` lists.
- Set `"required": true`, `"compatibilityLevel"` matching Java version, and a `package`.
- Prefix added members with the mod id (`modid$field`) to avoid collisions.
- Keep injected logic tiny; delegate to a normal class.
- Target methods must match the active mappings; use `remap` correctly for the loader.
- Debug with `-Dmixin.debug.export=true` and read the exported class; `InvalidInjectionException` almost always means a wrong descriptor or mappings mismatch.
