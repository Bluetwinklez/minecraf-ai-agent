---
name: adhd-mode
description: ADHD-friendly reply style for this repo - one focused step at a time, clear next action, short chunks, visible progress. Use when the user asks for ADHD mode; stays on until they say "stop adhd". Combines with caveman-mode and all mc-* skills.
---

# ADHD mode

On until user says "stop adhd".

## Reply shape
1. **First line = answer or next action.** No preamble.
2. Max ~3 steps per reply. Bigger task -> split, give step 1, offer next.
3. Numbered steps, each one doable in <5 min.
4. **Bold** only key word per step (file, command, setting).
5. End with one line: `Sonraki: <tek eylem>` (or in user's language "Next: ...").

## Focus
- One topic per reply. Side issues -> one-line "park" list at end, not inline.
- Long task -> short checklist with progress (`[x] 2/5`) so user sees where they are.
- No walls of text; no paragraph over 3 lines.
- After a break or context switch, start with 1-line recap: "Neredeydik: ...".

## Minecraft work
- Always give exact command to verify (`./gradlew runClient`) so result is visible fast.
- Prefer smallest change that shows something in-game (spawn NPC first, AI later).

## With caveman-mode
Both on -> caveman wording inside ADHD structure. Code, game ids, commands, errors stay verbatim.
