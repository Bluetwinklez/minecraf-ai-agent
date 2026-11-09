package com.bluetwinklez.aibuilders.build;

import com.bluetwinklez.aibuilders.AiBuilders;
import com.bluetwinklez.aibuilders.build.schematic.SchematicStore;
import com.bluetwinklez.aibuilders.config.AiBuildersConfig;
import com.bluetwinklez.aibuilders.entity.AgentNpc;
import com.bluetwinklez.aibuilders.task.AgentTask;
import com.bluetwinklez.aibuilders.task.TaskRunner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Builds a schematic block by block like a player would: walks within reach, takes materials from
 * its inventory or nearby chests, breaks blocks in the way and climbs scaffolding for high parts.
 */
public class BuildTask implements AgentTask {
	public static final String TYPE = "build";
	/** Place without neighbour shape updates: schematic states are already final (fences, stairs...). */
	private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	private static final int LOOKAHEAD = 256;
	private static final int STUCK_TICKS = 200;
	private static final int MAX_DEFERRALS = 3;
	private static final int REPATH_TICKS = 40;
	private static final int MAX_REPORT_LINES = 10;

	private enum Phase { LOADING, BUILDING, FETCHING, WAITING }

	private final String schematicName;
	private final BlockPos origin;
	private final Mirror mirror;
	private final Rotation rotation;
	private long fingerprint;
	private final boolean resumed;
	/** Captured at start so changing the config does not flip a running build. */
	private final AiBuildersConfig.BuildMode mode;

	private @Nullable CompletableFuture<BuildPlan> loading;
	private @Nullable BuildPlan plan;
	private boolean[] done = new boolean[0];
	private int[] deferrals = new int[0];
	private long[] deferredUntil = new long[0];
	private int cursor;
	private int doneCount;
	private int unreachable;
	private int unplaceable;
	private int blocked;

	private Phase phase = Phase.LOADING;
	private double budget;
	private int currentIndex = -1;
	private long currentSince;
	private @Nullable BlockPos navTarget;
	private long lastRepath;
	private @Nullable BlockPos fetchFrom;
	private List<BlockPos> containers = List.of();
	private long containersScannedAt = Long.MIN_VALUE;
	private long lastRecheck;
	private List<Map.Entry<Item, Integer>> lastReportedMissing = List.of();
	private @Nullable Item waitingFor;
	/** Containers that had the item but nothing could be taken (NPC inventory full), until tick. */
	private final Map<BlockPos, Long> skipContainerUntil = new HashMap<>();

	/** Scaffold column: x/z outside the build box, base = first block of the column. */
	private @Nullable BlockPos scaffoldBase;
	private int scaffoldHeight;

	private @Nullable ServerBossEvent bossBar;

	public BuildTask(String schematicName, BlockPos origin, Mirror mirror, Rotation rotation) {
		this(schematicName, origin, mirror, rotation, AiBuildersConfig.get().buildMode);
	}

	public BuildTask(String schematicName, BlockPos origin, Mirror mirror, Rotation rotation, AiBuildersConfig.BuildMode mode) {
		this(schematicName, origin, mirror, rotation, SchematicStore.fingerprint(schematicName), false, mode);
	}

	private BuildTask(String schematicName, BlockPos origin, Mirror mirror, Rotation rotation, long fingerprint, boolean resumed, AiBuildersConfig.BuildMode mode) {
		this.mode = mode;
		this.schematicName = schematicName;
		this.origin = origin;
		this.mirror = mirror;
		this.rotation = rotation;
		this.fingerprint = fingerprint;
		this.resumed = resumed;
	}

	public static void register() {
		TaskRunner.registerLoader(TYPE, BuildTask::load);
	}

	private static Optional<AgentTask> load(ValueInput input) {
		Optional<BlockPos> origin = input.read("origin", BlockPos.CODEC);
		String name = input.getStringOr("schematic", "");
		if (origin.isEmpty() || name.isEmpty()) {
			return Optional.empty();
		}
		BuildTask task = new BuildTask(
			name,
			origin.get(),
			input.read("mirror", Mirror.CODEC).orElse(Mirror.NONE),
			input.read("rotation", Rotation.CODEC).orElse(Rotation.NONE),
			input.getLongOr("fingerprint", -1L),
			true,
			input.read("mode", AiBuildersConfig.BuildMode.CODEC).orElse(AiBuildersConfig.get().buildMode)
		);
		task.unreachable = input.getIntOr("unreachable", 0);
		task.unplaceable = input.getIntOr("unplaceable", 0);
		task.blocked = input.getIntOr("blocked", 0);
		input.read("scaffold_base", BlockPos.CODEC).ifPresent(p -> task.scaffoldBase = p);
		task.scaffoldHeight = input.getIntOr("scaffold_height", 0);
		return Optional.of(task);
	}

	@Override
	public void save(ValueOutput output) {
		output.putString("schematic", schematicName);
		output.store("origin", BlockPos.CODEC, origin);
		output.store("mirror", Mirror.CODEC, mirror);
		output.store("rotation", Rotation.CODEC, rotation);
		output.putLong("fingerprint", fingerprint);
		output.store("mode", AiBuildersConfig.BuildMode.CODEC, mode);
		output.putInt("unreachable", unreachable);
		output.putInt("unplaceable", unplaceable);
		output.putInt("blocked", blocked);
		output.storeNullable("scaffold_base", BlockPos.CODEC, scaffoldBase);
		output.putInt("scaffold_height", scaffoldHeight);
	}

	@Override
	public String type() {
		return TYPE;
	}

	private static AiBuildersConfig config() {
		return AiBuildersConfig.get();
	}

	private boolean survival() {
		return mode == AiBuildersConfig.BuildMode.SURVIVAL;
	}

	// --- tick -----------------------------------------------------------------

	@Override
	public State tick(AgentNpc npc, ServerLevel level) {
		long now = level.getGameTime();
		if (phase == Phase.LOADING) {
			return tickLoading(npc, level);
		}
		updateBossBar(npc, level, now);
		budget = Math.min(budget + config().blocksPerSecond / 20.0, 2.0);

		if (phase == Phase.WAITING) {
			return tickWaiting(npc, level, now);
		}
		if (phase == Phase.FETCHING) {
			tickFetching(npc, level);
			return State.RUNNING;
		}

		// Skip finished entries at the front.
		while (cursor < done.length && done[cursor]) {
			cursor++;
		}
		if (cursor >= done.length) {
			if (scaffoldHeight > 0) {
				if (budget >= 1) {
					budget--;
					descendScaffold(npc, level);
				}
				return State.RUNNING;
			}
			finish(npc, level);
			return State.DONE;
		}

		int index = pickTarget(npc, level, now);
		if (Boolean.getBoolean("aibuilders.debug") && now % 10 == 0) {
			AiBuilders.LOGGER.info("[dbg {}] phase={} idx={} cursor={} done={}/{} npc={} target={} inReach={} scaffold={}@{} nav={}",
				schematicName, phase, index, cursor, doneCount, done.length, npc.blockPosition(), index >= 0 ? plan().pos(index) : null,
				index >= 0 && inReach(npc, plan().pos(index)), scaffoldHeight, scaffoldBase, navTarget);
		}
		if (index < 0) {
			return State.RUNNING;
		}
		if (index != currentIndex) {
			currentIndex = index;
			currentSince = now;
			navTarget = null;
		}
		BuildPlan p = plan();
		BlockPos pos = p.pos(index);
		BlockState wanted = p.state(index);

		if (survival()) {
			MaterialResolver.Cost cost = MaterialResolver.cost(wanted);
			if (cost instanceof MaterialResolver.Unplaceable) {
				unplaceable++;
				markDone(index);
				return State.RUNNING;
			}
			if (cost instanceof MaterialResolver.Needs needs && MaterialInventory.countIn(npc.getInventory(), needs.item()) < needs.count()) {
				return startFetchOrWait(npc, level, needs.item());
			}
		}

		if (!inReach(npc, pos)) {
			moveTowards(npc, level, index, pos, now);
			return State.RUNNING;
		}
		if (budget < 1) {
			return State.RUNNING;
		}
		if (npc.getBoundingBox().intersects(new AABB(pos)) && !wanted.getCollisionShape(level, pos).isEmpty()) {
			// Standing where the block goes: step aside and come back to it later.
			defer(index, now, 20);
			stepAside(npc, level, pos);
			return State.RUNNING;
		}

		BlockState current = level.getBlockState(pos);
		if (!current.isAir() && !current.canBeReplaced() && current != wanted) {
			if (!breakObstruction(npc, level, pos, current)) {
				blocked++;
				markDone(index);
			}
			budget--;
			return State.RUNNING;
		}
		place(npc, level, index, pos, wanted);
		budget--;
		return State.RUNNING;
	}

	private BuildPlan plan() {
		if (plan == null) {
			throw new IllegalStateException("plan not loaded");
		}
		return plan;
	}

	private State tickLoading(AgentNpc npc, ServerLevel level) {
		if (loading == null) {
			if (resumed && fingerprint != SchematicStore.fingerprint(schematicName)) {
				notifyOwner(npc, level, Component.translatable("aibuilders.build.schematic_changed", schematicName).withStyle(ChatFormatting.RED));
				return State.FAILED;
			}
			loading = SchematicStore.load(level.getServer(), schematicName)
				.thenApply(s -> BuildPlan.create(s, origin, mirror, rotation));
		}
		if (!loading.isDone()) {
			return State.RUNNING;
		}
		try {
			plan = loading.join();
		} catch (CompletionException | java.util.concurrent.CancellationException e) {
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			AiBuilders.LOGGER.warn("Could not load schematic {}", schematicName, cause);
			notifyOwner(npc, level, Component.translatable("aibuilders.build.load_failed", schematicName, cause.getMessage()).withStyle(ChatFormatting.RED));
			return State.FAILED;
		}
		int n = plan.size();
		done = new boolean[n];
		deferrals = new int[n];
		deferredUntil = new long[n];
		phase = Phase.BUILDING;
		fingerprint = SchematicStore.fingerprint(schematicName);
		npc.say(level, Component.translatable(resumed ? "aibuilders.build.resumed" : "aibuilders.build.started", schematicName), config().chatRadius);
		if (survival()) {
			refreshContainers(npc, level, true);
			List<Map.Entry<Item, Integer>> missing = missing(npc, level);
			if (!missing.isEmpty()) {
				reportMissing(npc, level, missing);
			}
		}
		return State.RUNNING;
	}

	// --- target selection -------------------------------------------------------

	/** Picks the next block: first eligible in order, preferring one already in reach. */
	private int pickTarget(AgentNpc npc, ServerLevel level, long now) {
		BuildPlan p = plan();
		int firstEligible = -1;
		int maxY = Integer.MAX_VALUE;
		int checks = 0;
		for (int i = cursor; i < done.length && checks < LOOKAHEAD; i++) {
			if (done[i]) {
				continue;
			}
			checks++;
			BlockPos pos = p.pos(i);
			if (maxY == Integer.MAX_VALUE) {
				maxY = pos.getY() + 1;
			} else if (pos.getY() > maxY) {
				break;
			}
			if (deferredUntil[i] > now) {
				continue;
			}
			if (!level.isLoaded(pos)) {
				defer(i, now, 100);
				continue;
			}
			if (level.getBlockState(pos) == p.state(i)) {
				markDone(i);
				continue;
			}
			if (firstEligible < 0) {
				firstEligible = i;
			}
			if (i == currentIndex || inReach(npc, pos)) {
				return i;
			}
		}
		return firstEligible;
	}

	private void markDone(int index) {
		if (!done[index]) {
			done[index] = true;
			doneCount++;
		}
	}

	private void defer(int index, long now, int ticks) {
		deferredUntil[index] = now + ticks;
		if (index == currentIndex) {
			currentIndex = -1;
		}
	}

	// --- movement ---------------------------------------------------------------

	private static double reach() {
		return config().reach;
	}

	private static boolean inReach(AgentNpc npc, BlockPos pos) {
		return npc.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= reach() * reach();
	}

	private void moveTowards(AgentNpc npc, ServerLevel level, int index, BlockPos pos, long now) {
		BlockPos base = scaffoldBase;
		if (base != null && scaffoldHeight > 0) {
			// On a scaffold: adjust height if the column can reach the target, otherwise climb down first.
			if (budget >= 1) {
				budget--;
				currentSince = now;
				if (scaffoldCanReach(pos) && npc.blockPosition().getY() < pos.getY() - 1) {
					climbScaffold(npc, level);
				} else {
					descendScaffold(npc, level);
				}
			}
			return;
		}
		if (base != null) {
			// Walking to the foot of a new scaffold column.
			Vec3 foot = Vec3.atBottomCenterOf(base);
			double dist = npc.position().distanceToSqr(foot);
			if (dist > 0.3 && dist < 2.6 && Math.abs(npc.getY() - base.getY()) < 0.6) {
				// Navigation stops about a block short; take the last step directly.
				npc.getNavigation().stop();
				npc.getMoveControl().setWantedPosition(foot.x, foot.y, foot.z, 1.0);
				if (now - currentSince > 40) {
					npc.teleportTo(foot.x, foot.y, foot.z);
				}
				return;
			}
			if (dist <= 0.3) {
				if (budget >= 1) {
					budget--;
					currentSince = now;
					climbScaffold(npc, level);
				}
				return;
			}
			if (npc.getNavigation().isDone()) {
				npc.getNavigation().moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 1.0);
			}
			if (now - currentSince >= STUCK_TICKS) {
				scaffoldBase = null;
			}
			checkStuck(npc, level, index, pos, now);
			return;
		}
		if (navTarget == null || now - lastRepath > REPATH_TICKS || npc.getNavigation().isDone()) {
			lastRepath = now;
			navTarget = findStandingSpot(npc, level, pos);
			if (navTarget == null && config().useScaffolding && pos.getY() > npc.getBlockY() + 2 && startScaffold(npc, level, pos)) {
				return;
			}
		}
		checkStuck(npc, level, index, pos, now);
	}

	private void checkStuck(AgentNpc npc, ServerLevel level, int index, BlockPos pos, long now) {
		if (now - currentSince < STUCK_TICKS) {
			return;
		}
		deferrals[index]++;
		if (deferrals[index] >= MAX_DEFERRALS) {
			BlockPos spot = config().allowTeleportWhenStuck ? findStandingSpot(npc, level, pos, false) : null;
			if (spot != null) {
				npc.getNavigation().stop();
				npc.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
				deferrals[index] = 0;
				currentSince = now;
				return;
			}
			unreachable++;
			markDone(index);
			currentIndex = -1;
			return;
		}
		defer(index, now, 400);
		navTarget = null;
	}

	private @Nullable BlockPos findStandingSpot(AgentNpc npc, ServerLevel level, BlockPos target) {
		return findStandingSpot(npc, level, target, true);
	}

	/** A place to stand from which {@code target} is in reach; optionally only ones the NPC can walk to. */
	private @Nullable BlockPos findStandingSpot(AgentNpc npc, ServerLevel level, BlockPos target, boolean needPath) {
		// Pathing stops roughly near a node, so pick spots well inside reach.
		double spotReach = Math.max(1.5, reach() - 1.0);
		int r = (int) Math.ceil(spotReach);
		List<BlockPos> spots = new ArrayList<>();
		Vec3 center = Vec3.atCenterOf(target);
		for (int dy = -3; dy <= 1; dy++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos feet = target.offset(dx, dy, dz);
					if (feet.equals(target) || feet.above().equals(target)) {
						continue;
					}
					Vec3 eye = new Vec3(feet.getX() + 0.5, feet.getY() + npc.getEyeHeight(), feet.getZ() + 0.5);
					if (eye.distanceToSqr(center) > spotReach * spotReach || !canStand(level, feet)) {
						continue;
					}
					spots.add(feet);
				}
			}
		}
		spots.sort(Comparator.comparingDouble(s -> s.distSqr(npc.blockPosition())));
		if (!needPath) {
			return spots.isEmpty() ? null : spots.getFirst();
		}
		int tries = 0;
		for (BlockPos spot : spots) {
			if (tries++ >= 4) {
				break;
			}
			if (npc.blockPosition().equals(spot)) {
				return spot;
			}
			Path path = npc.getNavigation().createPath(spot, 0);
			if (path != null && path.canReach()) {
				npc.getNavigation().moveTo(path, 1.0);
				return spot;
			}
		}
		return null;
	}

	private static boolean canStand(ServerLevel level, BlockPos feet) {
		if (!level.isLoaded(feet)) {
			return false;
		}
		BlockPos below = feet.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
			&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
			&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
	}

	private void stepAside(AgentNpc npc, ServerLevel level, BlockPos pos) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos spot = pos.relative(d, 2);
			if (canStand(level, spot)) {
				npc.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
				return;
			}
		}
	}

	// --- scaffolding ------------------------------------------------------------

	/** Picks a column spot just outside the build box next to {@code target} and walks there. */
	private boolean startScaffold(AgentNpc npc, ServerLevel level, BlockPos target) {
		BuildPlan p = plan();
		int x = Math.clamp(target.getX(), p.min().getX(), p.max().getX());
		int z = Math.clamp(target.getZ(), p.min().getZ(), p.max().getZ());
		// Push out of the box through the nearest side.
		int toMinX = x - p.min().getX(), toMaxX = p.max().getX() - x, toMinZ = z - p.min().getZ(), toMaxZ = p.max().getZ() - z;
		int best = Math.min(Math.min(toMinX, toMaxX), Math.min(toMinZ, toMaxZ));
		if (best == toMinX) {
			x = p.min().getX() - 1;
		} else if (best == toMaxX) {
			x = p.max().getX() + 1;
		} else if (best == toMinZ) {
			z = p.min().getZ() - 1;
		} else {
			z = p.max().getZ() + 1;
		}
		// Ground for the column: highest standable spot near the bottom of the build (no heightmap,
		// it would find roofs, trees or anything overhead).
		BlockPos base = null;
		for (int y = p.min().getY() + 1; y >= p.min().getY() - 6; y--) {
			BlockPos candidate = new BlockPos(x, y, z);
			if (canStand(level, candidate)) {
				base = candidate;
				break;
			}
		}
		if (base == null) {
			return false;
		}
		int groundY = base.getY();
		if (new Vec3(x + 0.5, target.getY() - 1 + npc.getEyeHeight(), z + 0.5).distanceToSqr(Vec3.atCenterOf(target)) > reach() * reach()) {
			return false;
		}
		if (survival() && MaterialInventory.countIn(npc.getInventory(), Items.SCAFFOLDING) < 1) {
			startFetchOrWait(npc, level, Items.SCAFFOLDING);
			return true;
		}
		scaffoldBase = base;
		scaffoldHeight = 0;
		navTarget = null;
		npc.getNavigation().moveTo(x + 0.5, groundY, z + 0.5, 1.0);
		return true;
	}

	private boolean scaffoldCanReach(BlockPos target) {
		BlockPos base = scaffoldBase;
		if (base == null) {
			return false;
		}
		Vec3 eye = new Vec3(base.getX() + 0.5, target.getY() - 1 + 1.62, base.getZ() + 0.5);
		return target.getY() - 1 >= base.getY() && eye.distanceToSqr(Vec3.atCenterOf(target)) <= reach() * reach();
	}

	/** Pillar up one block: lift the NPC and put scaffolding under its feet. */
	private void climbScaffold(AgentNpc npc, ServerLevel level) {
		BlockPos base = scaffoldBase;
		if (base == null) {
			return;
		}
		BlockPos place = base.above(scaffoldHeight);
		if (!level.getBlockState(place.above()).getCollisionShape(level, place.above()).isEmpty()
			|| !level.getBlockState(place.above(2)).getCollisionShape(level, place.above(2)).isEmpty()) {
			return;
		}
		if (survival()) {
			if (MaterialInventory.take(npc.getInventory(), Items.SCAFFOLDING, 1) < 1) {
				startFetchOrWait(npc, level, Items.SCAFFOLDING);
				return;
			}
		}
		npc.getNavigation().stop();
		npc.teleportTo(place.getX() + 0.5, place.getY() + 1, place.getZ() + 0.5);
		level.setBlock(place, Blocks.SCAFFOLDING.defaultBlockState(), Block.UPDATE_ALL);
		playPlaceSound(level, place, Blocks.SCAFFOLDING.defaultBlockState());
		npc.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		scaffoldHeight++;
	}

	/** Take the top scaffolding block back and step down. */
	private void descendScaffold(AgentNpc npc, ServerLevel level) {
		BlockPos base = scaffoldBase;
		if (base == null || scaffoldHeight <= 0) {
			scaffoldBase = null;
			scaffoldHeight = 0;
			return;
		}
		BlockPos top = base.above(scaffoldHeight - 1);
		if (level.getBlockState(top).is(Blocks.SCAFFOLDING)) {
			level.setBlock(top, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			if (survival()) {
				giveOrDrop(npc, level, new ItemStack(Items.SCAFFOLDING));
			}
		}
		scaffoldHeight--;
		npc.teleportTo(base.getX() + 0.5, base.getY() + scaffoldHeight, base.getZ() + 0.5);
		if (scaffoldHeight == 0) {
			scaffoldBase = null;
			navTarget = null;
		}
	}

	// --- placing / breaking -----------------------------------------------------

	private void place(AgentNpc npc, ServerLevel level, int index, BlockPos pos, BlockState wanted) {
		if (survival() && MaterialResolver.cost(wanted) instanceof MaterialResolver.Needs needs) {
			MaterialInventory.take(npc.getInventory(), needs.item(), needs.count());
			npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(needs.item()));
		} else if (!survival()) {
			Item item = wanted.getBlock().asItem();
			npc.setItemSlot(EquipmentSlot.MAINHAND, item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item));
		}
		npc.getLookControl().setLookAt(Vec3.atCenterOf(pos));
		level.setBlock(pos, wanted, PLACE_FLAGS);
		playPlaceSound(level, pos, wanted);
		npc.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		markDone(index);
		currentIndex = -1;
	}

	private static void playPlaceSound(ServerLevel level, BlockPos pos, BlockState state) {
		SoundType sound = state.getSoundType();
		level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
	}

	/** Breaks a block that is in the way; returns false if it must be left alone. */
	private boolean breakObstruction(AgentNpc npc, ServerLevel level, BlockPos pos, BlockState state) {
		if (!config().clearObstructions || state.getDestroySpeed(level, pos) < 0 || MaterialInventory.containerAt(level, pos) != null) {
			return false;
		}
		BlockEntity be = level.getBlockEntity(pos);
		List<ItemStack> drops = survival() ? Block.getDrops(state, level, pos, be) : List.of();
		npc.getLookControl().setLookAt(Vec3.atCenterOf(pos));
		npc.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		level.destroyBlock(pos, false, npc, 512);
		for (ItemStack drop : drops) {
			giveOrDrop(npc, level, drop);
		}
		return true;
	}

	private void giveOrDrop(AgentNpc npc, ServerLevel level, ItemStack stack) {
		ItemStack rest = npc.getInventory().addItem(stack);
		if (!rest.isEmpty()) {
			npc.spawnAtLocation(level, rest);
		}
	}

	// --- materials --------------------------------------------------------------

	private void refreshContainers(AgentNpc npc, ServerLevel level, boolean force) {
		long now = level.getGameTime();
		if (force || now - containersScannedAt > 100) {
			containersScannedAt = now;
			BlockPos center = plan != null ? BlockPos.containing(Vec3.atCenterOf(plan.min()).add(Vec3.atCenterOf(plan.max())).scale(0.5)) : npc.blockPosition();
			List<BlockPos> found = new ArrayList<>(MaterialInventory.containersAround(level, center, config().chestSearchRadius));
			for (BlockPos p : MaterialInventory.containersAround(level, npc.blockPosition(), config().chestSearchRadius)) {
				if (!found.contains(p)) {
					found.add(p);
				}
			}
			containers = found;
		}
	}

	/** Materials needed for the blocks not placed yet (plus scaffolding when it may be needed). */
	public BillOfMaterials remaining(ServerLevel level) {
		BillOfMaterials bom = new BillOfMaterials();
		if (plan == null) {
			return bom;
		}
		for (int i = 0; i < done.length; i++) {
			if (done[i]) {
				continue;
			}
			BlockPos pos = plan.pos(i);
			BlockState wanted = plan.state(i);
			if (level.isLoaded(pos) && level.getBlockState(pos) == wanted) {
				continue;
			}
			bom.add(MaterialResolver.cost(wanted));
		}
		return bom;
	}

	/** Scaffolding needed to reach the highest remaining block from the ground (returned later). */
	private int scaffoldNeed(ServerLevel level) {
		if (plan == null || !config().useScaffolding) {
			return 0;
		}
		int top = Integer.MIN_VALUE;
		for (int i = 0; i < done.length; i++) {
			if (!done[i]) {
				top = Math.max(top, plan.pos(i).getY());
			}
		}
		if (top == Integer.MIN_VALUE) {
			return 0;
		}
		int ground = plan.min().getY();
		int height = top - 1 - ground - 2;
		return Math.max(0, height - scaffoldHeight);
	}

	private List<Map.Entry<Item, Integer>> missing(AgentNpc npc, ServerLevel level) {
		BillOfMaterials bom = remaining(level);
		int scaffold = scaffoldNeed(level);
		if (scaffold > 0) {
			bom.add(Items.SCAFFOLDING, scaffold);
		}
		return bom.missing(MaterialInventory.available(level, npc, containers));
	}

	private State startFetchOrWait(AgentNpc npc, ServerLevel level, Item item) {
		refreshContainers(npc, level, false);
		BlockPos source = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos p : containers) {
			Container c = MaterialInventory.containerAt(level, p);
			if (skipContainerUntil.getOrDefault(p, Long.MIN_VALUE) > level.getGameTime()) {
				continue;
			}
			if (c != null && MaterialInventory.countIn(c, item) > 0) {
				double d = p.distSqr(npc.blockPosition());
				if (d < bestDist) {
					bestDist = d;
					source = p;
				}
			}
		}
		if (source != null) {
			fetchFrom = source;
			phase = Phase.FETCHING;
			navTarget = null;
			return State.RUNNING;
		}
		waitingFor = item;
		phase = Phase.WAITING;
		lastRecheck = level.getGameTime();
		npc.getNavigation().stop();
		List<Map.Entry<Item, Integer>> missing = missing(npc, level);
		if (missing.isEmpty()) {
			missing = List.of(Map.entry(item, 1));
		}
		if (!missing.equals(lastReportedMissing)) {
			reportMissing(npc, level, missing);
		}
		return State.WAITING_MATERIALS;
	}

	private void tickFetching(AgentNpc npc, ServerLevel level) {
		BlockPos source = fetchFrom;
		Container container = source == null ? null : MaterialInventory.containerAt(level, source);
		if (source == null || container == null) {
			phase = Phase.BUILDING;
			return;
		}
		if (npc.getEyePosition().distanceToSqr(Vec3.atCenterOf(source)) > reach() * reach()) {
			if (scaffoldHeight > 0) {
				descendScaffold(npc, level);
				return;
			}
			if (npc.getNavigation().isDone()) {
				BlockPos spot = findStandingSpot(npc, level, source);
				if (spot == null) {
					npc.getNavigation().moveTo(source.getX() + 0.5, source.getY(), source.getZ() + 0.5, 1.0);
				}
			}
			return;
		}
		npc.getNavigation().stop();
		npc.getLookControl().setLookAt(Vec3.atCenterOf(source));
		Map<Item, Integer> wanted = new HashMap<>(remaining(level).items());
		int scaffold = scaffoldNeed(level);
		if (scaffold > 0) {
			wanted.merge(Items.SCAFFOLDING, scaffold, Integer::sum);
		}
		// Only take what is still missing from the NPC's own inventory.
		Map<Item, Integer> own = new HashMap<>();
		MaterialInventory.count(npc.getInventory(), own);
		wanted.replaceAll((item, n) -> n - own.getOrDefault(item, 0));
		int moved = MaterialInventory.transfer(container, npc.getInventory(), wanted);
		level.playSound(null, source, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
		npc.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		if (moved == 0) {
			// Inventory full or the chest changed; do not walk back here for a while.
			skipContainerUntil.put(source, level.getGameTime() + 200);
			if (isFull(npc.getInventory())) {
				notifyOwner(npc, level, Component.translatable("aibuilders.npc.inventory_full"));
			}
		}
		fetchFrom = null;
		phase = Phase.BUILDING;
	}

	private static boolean isFull(Container container) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private Item waitingForOr(Item fallback) {
		return waitingFor != null ? waitingFor : fallback;
	}

	private State tickWaiting(AgentNpc npc, ServerLevel level, long now) {
		if (now - lastRecheck < config().materialRecheckSeconds * 20L) {
			return State.WAITING_MATERIALS;
		}
		lastRecheck = now;
		refreshContainers(npc, level, true);
		Item item = waitingForOr(Items.AIR);
		Map<Item, Integer> available = MaterialInventory.available(level, npc, containers);
		if (available.getOrDefault(item, 0) > 0) {
			waitingFor = null;
			lastReportedMissing = List.of();
			phase = Phase.BUILDING;
			npc.say(level, Component.translatable("aibuilders.build.continuing"), config().chatRadius);
			return State.RUNNING;
		}
		List<Map.Entry<Item, Integer>> missing = missing(npc, level);
		if (!missing.isEmpty() && !missing.equals(lastReportedMissing)) {
			reportMissing(npc, level, missing);
		}
		return State.WAITING_MATERIALS;
	}

	// --- reporting --------------------------------------------------------------

	private void reportMissing(AgentNpc npc, ServerLevel level, List<Map.Entry<Item, Integer>> missing) {
		lastReportedMissing = List.copyOf(missing);
		MutableComponent summary = Component.translatable("aibuilders.materials.missing_header", missing.size()).withStyle(ChatFormatting.GOLD);
		ServerPlayer owner = npc.ownerPlayer(level);
		if (owner != null) {
			npc.tell(owner, summary);
			for (Component line : BillOfMaterials.lines(missing, MAX_REPORT_LINES)) {
				owner.sendSystemMessage(line);
			}
			if (missing.size() > MAX_REPORT_LINES) {
				owner.sendSystemMessage(Component.translatable("aibuilders.materials.see_command", npc.npcName()).withStyle(ChatFormatting.GRAY));
			}
		}
		// Short one-liner for everybody else nearby.
		MutableComponent shortLine = Component.translatable("aibuilders.materials.missing_short");
		for (int i = 0; i < Math.min(3, missing.size()); i++) {
			shortLine.append(i == 0 ? Component.literal(" ") : Component.literal(", "));
			shortLine.append(BillOfMaterials.line(missing.get(i).getKey(), missing.get(i).getValue()));
		}
		if (missing.size() > 3) {
			shortLine.append(Component.literal(", ..."));
		}
		double r = config().chatRadius;
		for (ServerPlayer player : level.players()) {
			if (player != owner && player.distanceToSqr(npc) <= r * r) {
				npc.tell(player, shortLine);
			}
		}
	}

	/** Full status for one player: progress, missing materials, skipped blocks. */
	public void reportTo(AgentNpc npc, ServerLevel level, ServerPlayer player, boolean full) {
		npc.tell(player, Component.translatable("aibuilders.build.status_line", schematicName, status()));
		if (plan == null) {
			return;
		}
		if (survival() && full) {
			refreshContainers(npc, level, false);
			List<Map.Entry<Item, Integer>> missing = missing(npc, level);
			if (missing.isEmpty()) {
				player.sendSystemMessage(Component.translatable("aibuilders.materials.none_missing").withStyle(ChatFormatting.GREEN));
			} else {
				player.sendSystemMessage(Component.translatable("aibuilders.materials.missing_header", missing.size()).withStyle(ChatFormatting.GOLD));
				for (Component line : BillOfMaterials.lines(missing, full ? Integer.MAX_VALUE : MAX_REPORT_LINES)) {
					player.sendSystemMessage(line);
				}
			}
		}
		if (unplaceable + unreachable + blocked > 0) {
			player.sendSystemMessage(Component.translatable("aibuilders.build.skipped", unplaceable, unreachable, blocked).withStyle(ChatFormatting.GRAY));
		}
	}

	private void notifyOwner(AgentNpc npc, ServerLevel level, Component message) {
		ServerPlayer owner = npc.ownerPlayer(level);
		if (owner != null) {
			npc.tell(owner, message);
		} else {
			npc.say(level, message, config().chatRadius);
		}
	}

	private void finish(AgentNpc npc, ServerLevel level) {
		npc.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		npc.say(level, Component.translatable("aibuilders.build.done", schematicName).withStyle(ChatFormatting.GREEN), config().chatRadius);
		ServerPlayer owner = npc.ownerPlayer(level);
		if (owner != null && unplaceable + unreachable + blocked > 0) {
			owner.sendSystemMessage(Component.translatable("aibuilders.build.skipped", unplaceable, unreachable, blocked).withStyle(ChatFormatting.GRAY));
		}
	}

	@Override
	public void onEnd(AgentNpc npc, ServerLevel level) {
		npc.getNavigation().stop();
		if (bossBar != null) {
			bossBar.removeAllPlayers();
			bossBar = null;
		}
		// Do not leave the NPC stuck on a tower when stopped mid-way.
		if (scaffoldHeight > 0 && scaffoldBase != null) {
			while (scaffoldHeight > 0) {
				descendScaffold(npc, level);
			}
		}
	}

	private void updateBossBar(AgentNpc npc, ServerLevel level, long now) {
		if (!config().showBossBar || now % 20 != 0) {
			return;
		}
		ServerPlayer owner = npc.ownerPlayer(level);
		if (bossBar == null) {
			bossBar = new ServerBossEvent(UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
		}
		bossBar.setName(Component.literal(npc.npcName() + ": " + schematicName + " - ").append(status()));
		bossBar.setProgress(progress());
		bossBar.setColor(phase == Phase.WAITING ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.GREEN);
		if (owner != null && !bossBar.getPlayers().contains(owner)) {
			bossBar.addPlayer(owner);
		}
	}

	public float progress() {
		return done.length == 0 ? 0F : (float) doneCount / done.length;
	}

	@Override
	public Component status() {
		return switch (phase) {
			case LOADING -> Component.translatable("aibuilders.status.loading");
			case WAITING -> Component.translatable("aibuilders.status.waiting");
			case FETCHING -> Component.translatable("aibuilders.status.fetching");
			case BUILDING -> scaffoldHeight > 0
				? Component.translatable("aibuilders.status.scaffold", Math.round(progress() * 100))
				: Component.translatable("aibuilders.status.building", Math.round(progress() * 100));
		};
	}

	public String schematicName() {
		return schematicName;
	}

	public boolean isWaiting() {
		return phase == Phase.WAITING;
	}
}
