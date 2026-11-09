---
name: mc-npc-interaction
description: Player-NPC interaction - right-click handling, dialogue GUIs, trading, quests, custom network packets, and per-player NPC data. Use when players talk to, trade with, or get quests from NPCs.
---

# NPC interaction

## Entry point
Override `mobInteract(Player, InteractionHand)` on the NPC. On server: open menu / start dialogue; return `InteractionResult.sidedSuccess(level().isClientSide)`.

## Dialogue UI
- Client-only `Screen` for dialogue; server stays authoritative.
- Flow: server -> packet (NPC id, text, options) -> client screen -> option chosen -> packet back -> server validates and acts.
- Inventory-like UI (trade, quest board): `AbstractContainerMenu` + `MenuType` + `AbstractContainerScreen`.

## Networking
- Fabric: `PayloadTypeRegistry` + `CustomPacketPayload` (1.20.5+), `ServerPlayNetworking` / `ClientPlayNetworking`.
- NeoForge: `RegisterPayloadHandlersEvent`, `PayloadRegistrar`.
- Always validate on server: entity exists, player within ~8 blocks, option valid. Never trust client.

## Trading / quests
- Vanilla-style trades: implement `Merchant`, `MerchantOffers`.
- Quest/relationship state per player: store on player (Fabric data attachments / NeoForge `AttachmentType`) or `SavedData` keyed by UUID.
- Lang keys for all dialogue text; never hardcode strings shown to players.
