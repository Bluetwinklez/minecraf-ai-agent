package com.bluetwinklez.aibuilders.build;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluids;

/** Works out which item (and how many) a survival player would need to place a block state. */
public final class MaterialResolver {
	/** What placing one schematic block costs. */
	public sealed interface Cost {
	}

	/** Consumes {@code count} of {@code item}. */
	public record Needs(Item item, int count) implements Cost {
	}

	/** Placed without consuming anything (second half of a door/bed, piston head...). */
	public record Free() implements Cost {
	}

	/** Cannot be placed in survival (no item, fluids, portals...). */
	public record Unplaceable() implements Cost {
	}

	private static final Free FREE = new Free();
	private static final Unplaceable UNPLACEABLE = new Unplaceable();
	private static final IntegerProperty[] COUNT_PROPERTIES = {
		BlockStateProperties.CANDLES, BlockStateProperties.PICKLES, BlockStateProperties.LAYERS,
		BlockStateProperties.EGGS, BlockStateProperties.FLOWER_AMOUNT, BlockStateProperties.SEGMENT_AMOUNT
	};

	private MaterialResolver() {
	}

	public static Cost cost(BlockState state) {
		if (state.isAir()) {
			return FREE;
		}
		// The other half is placed together with the counted half.
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			return FREE;
		}
		if (state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
			return FREE;
		}
		if (state.is(Blocks.PISTON_HEAD)) {
			return FREE;
		}
		if (state.is(Blocks.WATER) || state.is(Blocks.LAVA) || state.is(Blocks.BUBBLE_COLUMN) || state.is(Blocks.MOVING_PISTON)) {
			return UNPLACEABLE;
		}
		if (!state.getFluidState().isEmpty() && state.getFluidState().getType() != Fluids.EMPTY && state.getBlock().asItem() == Items.AIR) {
			return UNPLACEABLE;
		}
		Item item = state.getBlock().asItem();
		if (item == Items.AIR) {
			return UNPLACEABLE;
		}
		int count = 1;
		if (state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) {
			count = 2;
		}
		for (IntegerProperty property : COUNT_PROPERTIES) {
			if (state.hasProperty(property)) {
				count = state.getValue(property);
			}
		}
		return new Needs(item, count);
	}

	/** True if {@code placed} already matches {@code wanted} closely enough to count as done. */
	public static boolean matches(BlockState placed, BlockState wanted) {
		return placed == wanted;
	}
}
