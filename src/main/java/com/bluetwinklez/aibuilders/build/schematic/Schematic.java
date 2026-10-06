package com.bluetwinklez.aibuilders.build.schematic;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A loaded schematic: one or more regions inside a bounding box that starts at (0,0,0).
 * Block data stays packed (palette + bit array) so big schematics stay cheap in memory.
 */
public final class Schematic {
	private final String name;
	private final Vec3i size;
	private final List<Region> regions;
	private final int skippedBlockEntities;
	private final int skippedEntities;
	private final int unknownBlocks;

	public Schematic(String name, Vec3i size, List<Region> regions, int skippedBlockEntities, int skippedEntities, int unknownBlocks) {
		this.name = name;
		this.size = size;
		this.regions = List.copyOf(regions);
		this.skippedBlockEntities = skippedBlockEntities;
		this.skippedEntities = skippedEntities;
		this.unknownBlocks = unknownBlocks;
	}

	/**
	 * One box of blocks. {@code offset} is the box's min corner inside the schematic,
	 * {@code size} is positive, entries are indexed {@code y * sx * sz + z * sx + x}.
	 */
	public record Region(String name, Vec3i offset, Vec3i size, BlockState[] palette, PackedBitArray blocks) {
		public long volume() {
			return (long) size.getX() * size.getY() * size.getZ();
		}

		public BlockState get(int x, int y, int z) {
			long index = ((long) y * size.getZ() + z) * size.getX() + x;
			int id = blocks.get(index);
			return id < palette.length ? palette[id] : Blocks.AIR.defaultBlockState();
		}
	}

	/** Visitor for every non-air block, in schematic space (before rotation). */
	@FunctionalInterface
	public interface BlockVisitor {
		void accept(int x, int y, int z, BlockState state);
	}

	public void forEachBlock(BlockVisitor visitor) {
		for (Region region : regions) {
			Vec3i o = region.offset();
			Vec3i s = region.size();
			for (int y = 0; y < s.getY(); y++) {
				for (int z = 0; z < s.getZ(); z++) {
					for (int x = 0; x < s.getX(); x++) {
						BlockState state = region.get(x, y, z);
						if (!state.isAir()) {
							visitor.accept(o.getX() + x, o.getY() + y, o.getZ() + z, state);
						}
					}
				}
			}
		}
	}

	public long countNonAir() {
		long[] count = {0};
		forEachBlock((x, y, z, state) -> count[0]++);
		return count[0];
	}

	public String name() {
		return name;
	}

	public Vec3i size() {
		return size;
	}

	public List<Region> regions() {
		return regions;
	}

	public int skippedBlockEntities() {
		return skippedBlockEntities;
	}

	public int skippedEntities() {
		return skippedEntities;
	}

	public int unknownBlocks() {
		return unknownBlocks;
	}

	/** Size of the bounding box after rotation (90/270 swap X and Z). */
	public static Vec3i transformedSize(Vec3i size, Rotation rotation) {
		return switch (rotation) {
			case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> new Vec3i(size.getZ(), size.getY(), size.getX());
			default -> size;
		};
	}

	/**
	 * Maps a position inside the schematic box to the box after mirroring then rotating,
	 * keeping the result inside {@code [0, transformedSize)} (same order vanilla uses).
	 */
	public static BlockPos transform(int x, int y, int z, Vec3i size, Mirror mirror, Rotation rotation) {
		int sx = size.getX();
		int sz = size.getZ();
		switch (mirror) {
			case LEFT_RIGHT -> z = sz - 1 - z;
			case FRONT_BACK -> x = sx - 1 - x;
			default -> {
			}
		}
		return switch (rotation) {
			case CLOCKWISE_90 -> new BlockPos(sz - 1 - z, y, x);
			case CLOCKWISE_180 -> new BlockPos(sx - 1 - x, y, sz - 1 - z);
			case COUNTERCLOCKWISE_90 -> new BlockPos(z, y, sx - 1 - x);
			default -> new BlockPos(x, y, z);
		};
	}

	public static BlockState transformState(BlockState state, Mirror mirror, Rotation rotation) {
		return state.mirror(mirror).rotate(rotation);
	}
}
