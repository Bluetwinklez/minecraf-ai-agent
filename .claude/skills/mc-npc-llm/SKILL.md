---
name: mc-npc-llm
description: Connect NPCs to an LLM (e.g. Claude API) for AI-driven dialogue and decisions - async HTTP, prompt/memory design, turning model output into safe in-game actions. Use when an NPC should talk or decide via an AI model.
---

# LLM-driven NPC

## Architecture
```
player chat/interact -> server collects context -> async HTTP to LLM
  -> response parsed -> back on server thread (server.execute) -> NPC speaks / acts
```

## Rules
- **Never call HTTP on the server tick thread.** Use `java.net.http.HttpClient.sendAsync` or a dedicated executor; apply results via `server.execute(() -> ...)`.
- API key from server config or env var (`ANTHROPIC_API_KEY`), never in code, never sent to clients.
- Model id in config (default e.g. `claude-sonnet-5-5`); short `max_tokens` for chat lines.
- Rate limit per NPC and per player; cancel/ignore stale replies if NPC unloaded or player left.
- Timeout + fallback line ("...") on error.

## Prompt design
- System prompt: NPC name, personality, role, world facts, allowed actions.
- Context: last N dialogue turns (bounded), nearby info (time, biome, held item), player relationship.
- Memory: summarize old turns into NPC saved data; keep raw history small.

## Actions from model output
- Ask for structured output (JSON or tool use) like `{"say": "...", "action": "follow|trade|idle|give_item", "args": {...}}`.
- Whitelist actions; map each to a server-side `Goal` trigger or method. Validate args (item ids exist, counts bounded). Never execute raw commands from model output.

## Claude Messages API minimal call
```
POST https://api.anthropic.com/v1/messages
headers: x-api-key, anthropic-version: 2023-06-01, content-type: application/json
body: {"model": "...", "max_tokens": 200, "system": "...", "messages": [{"role":"user","content":"..."}]}
```
