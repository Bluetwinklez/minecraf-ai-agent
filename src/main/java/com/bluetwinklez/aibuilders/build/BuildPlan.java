package com.bluetwinklez.aibuilders.build;

import com.bluetwinklez.aibuilders.build.schematic.Schematic;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ordered list of world positions and the states to place there.
 *
 * <p>Order: solid blocks bottom-up, then non-solid blocks (torches, carpets, plants, redstone...)
 * bottom-up, then fluids. Within a layer rows snake back and forth so the NPC walks less.
 */
public final class BuildPlan {
	private final long[] positions;
	private final BlockState[] states;
	private final BlockPos min;
	private final BlockPos max;

	private BuildPlan(long[] positions, BlockState[] states, BlockPos min, BlockPos max) {
		this.positions = positions;
		this.states = states;
		this.min = min;
		this.max = max;
	}

	public static BuildPlan create(Schematic schematic, BlockPos origin, Mirror mirror, Rotation rotation) {
		Vec3i size = schematic.size();
		int count = (int) schematic.countNonAir();
		long[] pos = new long[count];
		BlockState[] st = new BlockState[count];
		long[] sortKey = new long[count];
		int[] i = {0};
		schematic.forEachBlock((x, y, z, state) -> {
			BlockPos rel = Schematic.transform(x, y, z, size, mirror, rotation);
			BlockState placed = Schematic.transformState(state, mirror, rotation);
			int n = i[0]++;
			pos[n] = origin.offset(rel).asLong();
			st[n] = placed;
			int pass = !placed.getFluidState().isEmpty() && placed.getBlock().asItem() == net.minecraft.world.item.Items.AIR ? 2 : placed.isSolid() ? 0 : 1;
			int sx = rel.getX();
			// Snake: odd rows walk x backwards.
			int xKey = (rel.getZ() & 1) == 0 ? sx : 0xFFFF - sx;
			sortKey[n] = ((long) pass << 60) | ((long) (rel.getY() & 0xFFFF) << 40) | ((long) (rel.getZ() & 0xFFFF) << 20) | (xKey & 0xFFFFF);
		});
		Integer[] order = new Integer[count];
		for (int k = 0; k < count; k++) {
			order[k] = k;
		}
		Arrays.sort(order, (a, b) -> Long.compare(sortKey[a], sortKey[b]));
		long[] sortedPos = new long[count];
		BlockState[] sortedStates = new BlockState[count];
		for (int k = 0; k < count; k++) {
			sortedPos[k] = pos[order[k]];
			sortedStates[k] = st[order[k]];
		}
		Vec3i box = Schematic.transformedSize(size, rotation);
		return new BuildPlan(sortedPos, sortedStates, origin, origin.offset(box.getX() - 1, box.getY() - 1, box.getZ() - 1));
	}

	public int size() {
		return positions.length;
	}

	public BlockPos pos(int index) {
		return BlockPos.of(positions[index]);
	}

	public BlockState state(int index) {
		return states[index];
	}

	public BlockPos min() {
		return min;
	}

	public BlockPos max() {
		return max;
	}

	public boolean insideBox(BlockPos p) {
		return p.getX() >= min.getX() && p.getX() <= max.getX()
			&& p.getY() >= min.getY() && p.getY() <= max.getY()
			&& p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
	}
}
