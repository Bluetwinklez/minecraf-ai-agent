---
name: mc-npc-ai
description: Program NPC behavior - Goal system, Brain/memory/sensors, pathfinding, schedules, following/guarding/working routines. Use for anything about how an NPC moves, decides, or reacts.
---

# NPC behavior / AI

## Goal system vs Brain
- **Goals** (`goalSelector`, `targetSelector` in `registerGoals`): simple, priority-based. Default choice.
- **Brain** (`Brain`, `MemoryModuleType`, `SensorType`, `Activity`): villager/piglin-style, schedules and memories. Use for complex daily routines.

## Common goals
`FloatGoal`, `LookAtPlayerGoal`, `RandomLookAroundGoal`, `WaterAvoidingRandomStrollGoal`, `OpenDoorGoal` (needs `GroundPathNavigation#setCanOpenDoors(true)`), `MoveTowardsRestrictionGoal`, `MeleeAttackGoal`, `NearestAttackableTargetGoal`.

## Custom goal template
```java
public class FollowOwnerNpcGoal extends Goal {
    private final MyNpc npc;
    public FollowOwnerNpcGoal(MyNpc npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    @Override public boolean canUse() { return npc.getOwner() != null && npc.distanceToSqr(npc.getOwner()) > 9; }
    @Override public void tick() { npc.getNavigation().moveTo(npc.getOwner(), 1.0D); }
}
```

## Rules
- AI runs server side only; guard with `!level().isClientSide`.
- Keep `canUse()` cheap - it runs every tick. Throttle expensive scans (`tickCount % 20 == 0`).
- Flags (`MOVE`, `LOOK`, `JUMP`, `TARGET`) decide which goals can run together.
- Long tasks (LLM calls, big searches) never block the tick; see `mc-npc-llm`.
- Debug paths: `/debugpath` (dev) or render `getNavigation().getPath()`.
