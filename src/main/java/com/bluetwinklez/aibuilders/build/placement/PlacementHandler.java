/*
 * Adapted from structurize (https://github.com/ldtteam/structurize, commit f66c19c),
 * com/ldtteam/structurize/placement/handlers/placement/IPlacementHandler.java, GPL-3.0.
 * Modified for AI Builders: server-side mob builder, item costs as MaterialResolver.Cost.
 */
package com.bluetwinklez.aibuilders.build.placement;

import com.bluetwinklez.aibuilders.build.MaterialResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Per-block rules for what a schematic block costs, how it is placed and when it counts as done. */
public interface PlacementHandler {
	enum Result {
		/** The block (and any partner block, e.g. the top half of a door) is in the world now. */
		PLACED,
		/** Cannot be placed yet (e.g. top half of a door before the bottom half): try again later. */
		DEFER
	}

	boolean canHandle(BlockState wanted);

	MaterialResolver.Cost cost(BlockState wanted);

	/** True if the block in the world already counts as {@code wanted}. */
	default boolean matches(BlockState world, BlockState wanted) {
		return world == wanted;
	}

	/** Puts {@code wanted} into the world. {@code flags} are the builder's {@code setBlock} flags. */
	default Result place(ServerLevel level, BlockPos pos, BlockState wanted, int flags) {
		level.setBlock(pos, wanted, flags);
		return Result.PLACED;
	}
}
