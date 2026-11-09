/*
 * Adapted from structurize (https://github.com/ldtteam/structurize, commit f66c19c),
 * com/ldtteam/structurize/placement/handlers/placement/PlacementHandlers.java, GPL-3.0,
 * and MineColonies (https://github.com/ldtteam/minecolonies, commit b2fb29ec),
 * com/minecolonies/core/placementhandlers/*, GPL-3.0.
 * Modified for AI Builders: no blueprint/colony context, costs as MaterialResolver.Cost,
 * multi-block halves placed together, Minecraft 26.x names.
 */
package com.bluetwinklez.aibuilders.build.placement;

import com.bluetwinklez.aibuilders.build.MaterialResolver;
import com.bluetwinklez.aibuilders.build.MaterialResolver.Cost;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Ordered list of placement handlers; the first one whose {@code canHandle} matches wins. */
public final class PlacementHandlers {
	private static final List<PlacementHandler> HANDLERS = new ArrayList<>();
	private static final Map<Block, PlacementHandler> CACHE = new IdentityHashMap<>();

	static {
		HANDLERS.add(new AirHandler());
		HANDLERS.add(new UnobtainableHandler());
		HANDLERS.add(new FluidHandler());
		HANDLERS.add(new FireHandler());
		HANDLERS.add(new DirtPathHandler());
		HANDLERS.add(new GrassHandler());
		HANDLERS.add(new DoorHandler());
		HANDLERS.add(new BedHandler());
		HANDLERS.add(new DoublePlantHandler());
		HANDLERS.add(new FlowerPotHandler());
		HANDLERS.add(new InfestedHandler());
		HANDLERS.add(new WeatheredCopperHandler());
		HANDLERS.add(new PistonHeadHandler());
		HANDLERS.add(new FallingBlockHandler());
		HANDLERS.add(new GeneralHandler());
	}

	private PlacementHandlers() {
	}

	public static PlacementHandler get(BlockState state) {
		synchronized (CACHE) {
			return CACHE.computeIfAbsent(state.getBlock(), b -> {
				for (PlacementHandler h : HANDLERS) {
					if (h.canHandle(state)) {
						return h;
					}
				}
				return HANDLERS.getLast();
			});
		}
	}

	private static Cost single(Item item, int count) {
		return item == Items.AIR || count <= 0 ? MaterialResolver.UNPLACEABLE : MaterialResolver.Needs.of(item, count);
	}

	// --- handlers ---------------------------------------------------------------------

	/** Air: nothing to pay, the world must be empty there. */
	static final class AirHandler implements PlacementHandler {
		@Override
		public boolean canHandle(BlockState wanted) {
			return wanted.isAir();
		}

		@Override
		public Cost cost(BlockState wanted) {
			return MaterialResolver.FREE;
		}

		@Override
		public boolean matches(BlockState world, BlockState wanted) {
			return world.isAir();
		}

		@Override
		public Result place(ServerLevel level, BlockPos pos, BlockState wanted, int flags) {
			level.removeBlock(pos, false);
			return Result.PLACED;
		}
	}

	/**
	 * Blocks a survival player cannot obtain (structurize SpecialBlockPlacementAttemptHandler and
	 * BlackListedBlockPlacementHandler, MineColonies BuilderIgnorePlacementHandler).
	 */
	static final class UnobtainableHandler implements PlacementHandler {
		private static final Set<Block> BLOCKS = Set.of(
			Blocks.END_PORTAL, Blocks.END_GATEWAY, Blocks.NETHER_PORTAL, Blocks.END_PORTAL_FRAME, Blocks.SPAWNER,
			Blocks.TRIAL_SPAWNER, Blocks.VAULT, Blocks.DRAGON_EGG, Blocks.BEDROCK, Blocks.BARRIER, Blocks.LIGHT,
			Blocks.STRUCTURE_VOID, Blocks.STRUCTURE_BLOCK, Blocks.JIGSAW, Blocks.COMMAND_BLOCK,
			Blocks.REPEATING_COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK, Blocks.TEST_BLOCK, Blocks.REINFORCED_DEEPSLATE,
			Blocks.BUDDING_AMETHYST, Blocks.FROSTED_ICE, Blocks.PETRIFIED_OAK_SLAB, Blocks.MOVING_PISTON, Blocks.BUBBLE_COLUMN
		);

		@Override
		public boolean canHandle(BlockState wanted) {
			return BLOCKS.contains(wanted.getBlock());
		}

		@Override
		public Cost cost(BlockState wanted) {
			return MaterialResolver.UNPLACEABLE;
		}
	}

	/** Water and lava: a source block costs a bucket (the empty bucket comes back); flowing fluid is ignored. */
	static final class FluidHandler implements PlacementHandler {
		@Override
		public boolean canHandle(BlockState wanted) {
			return wanted.getBlock() instanceof LiquidBlock;
		}

		@Override
		public Cost cost(BlockState wanted) {
			if (!wanted.getFluidState().isSource()) {
				return MaterialResolver.FREE;
			}
			Item bucket = wanted.is(Blocks.LAVA) ? Items.LAVA_BUCKET : Items.WATER_BUCKET;
			return new MaterialResolver.Needs(Map.of(bucket, 1), List.of(), Map.of(Items.BUCKET, 1));
		}

		@Override
		public boolean matches(BlockState world, BlockState wanted) {
			// Flowing fluid is whatever the source makes of it; a source must be a source of the same fluid.
			return !wanted.getFluidState().isSource() || world.getFluidState().isSource() && world.getFluidState().getType() == wanted.getFluidState().getType();
		}
	}

	/** Fire needs flint and steel (kept, not used up). */
	static final class FireHandler implements PlacementHandler {
		@Override
		public boolean canHandle(BlockState wanted) {
			return wanted.is(Blocks.FIRE) || wanted.is(Blocks.SOUL_FIRE);
		}

		@Override
		public Cost cost(BlockState wanted) {
			return new MaterialResolver.Needs(Map.of(), List.of(Items.FLINT_AND_STEEL), Map.of());
		}
	}

	/** Dirt path and farmland are made from dirt. */
	static final class DirtPathHandler implements PlacementHandler {
		@Override
		public boolean canHandle(BlockState wanted) {
			return wanted.is(Blocks.DIRT_PATH) || wanted.is(Blocks.FARMLAND);
		}

		@Override
		public Cost cost(BlockState wanted) {
			return MaterialResolver.Needs.of(Items.DIRT, 1);
		}

		@Override
		public boolean matches(BlockState world, BlockState wanted) {
			return world.getBlock() == wanted.getBlock();
		}
	}

	/** Grass-like soil costs dirt; any dirt-family block in the world counts as done. */
	static final class GrassHandler implements PlacementHandler {
		@Override
		public boolean canHandle(BlockState wanted) {
			return wanted.is(Blocks.GRASS_BLOCK) || wanted.is(Blocks.PODZOL) || wanted.is(Blocks.MYCELIUM);
		}

		@Override
		public Cost cost(BlockState wanted) {
			return MaterialResolver.Needs.of(Items.DIRT, 1);
		}

		@Override
		public boolean matches(BlockState world, BlockState wanted) {
			return world.is(BlockTags.DIRT);
		}
	}

	/** Doors: the lower half costs the door and places both halves; open/powered do not matter. */
	static final class DoorHandler implements PlacementHandler {
		@Override
		public boolean canHandle(BlockState wanted) {
			return wanted.getBlock() instanceof DoorBlock;
		}

		@Override
		public Cost cost(BlockState wanted) {
			return wanted.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? single(wanted.getBlock().asItem(), 1) : MaterialResolver.FREE;
		}

		@Override
		public boolean matches(BlockState world, BlockState wanted) {
			return world.getBlock() == wanted.getBlock() && sameIgnoring(world, wanted, Set.of("open", "powered"));
		}

		@Override
		public Result place(ServerLevel level, BlockPos pos, BlockState wanted, int flags) {
			return placeTwoHigh(level, pos, wanted, flags, this);
		}
	}

	/** Beds: the foot costs the bed and places both parts. */
	static final class BedHandler implements PlacementHandler {
		@Override
		public boolean canHandle(BlockState wanted) {
			return wanted.getBlock() instanceof BedBlock;
		}

		@Override
		public Cost cost(BlockState wanted) {
			return wanted.getValue(BedBlock.PART) == BedPart.FOOT ? single(wanted.getBlock().asItem(), 1) : MaterialResolver.FREE;
		}

		@Override
		public boolean matches(BlockState world, BlockState wanted) {
			return world.getBlock() == wanted.getBlock() && sameIgnoring(world, wanted, Set.of("occupied"));
		}

		@Override
		public Result place(ServerLevel level, BlockPos pos, BlockState wanted, int flags) {
			if (wanted.getValue(BedBlock.PART) != BedPart.FOOT) {
				return matches(level.getBlockState(pos), wanted) ? Result.PLACED : Result.DEFER;
			}
			BlockPos headPos = pos.relative(wanted.getValue(BedBlock.FACING));
			level.setBlock(pos, wanted, flags);
			level.setBlock(headPos, wanted.setValue(BedBlock.PART, BedPart.HEAD), flags);
			return Result.PLACED;
		}
	}

	/** Tall plants and other two-block-high blocks: the lower half costs one item and places both halves. */
	static final class DoublePlantHandler implements PlacementHandler {
		@Override
		public boolean canHandle(BlockState wanted) {
			return wanted.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
		}

		@Override
		public Cost cost(BlockState wanted) {
			return wanted.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
				? GeneralHandler.counted(wanted) : MaterialResolver.FREE;
		}

		@Override
		public Result place(ServerLevel level, BlockPos pos, BlockState wanted, int flags) {
			return placeTwoHigh(level, pos, wanted, flags, this);
		}
	}

	/** A filled flower pot costs the pot plus the plant. */
	static final class FlowerPotHandler implements PlacementHandler {
		@Override
		public boolean canHandle(BlockState wanted) {
			return wanted.getBlock() instanceof FlowerPotBlock;
		}

		@Override
		public Cost cost(BlockState wanted) {
			Block plant = ((FlowerPotBlock) wanted.getBlock()).getPotted();
			Map<Item, Integer> items = new LinkedHashMap<>();
			items.put(Items.FLOWER_POT, 1);
			if (plant != Blocks.AIR && plant.asItem() != Items.AIR) {
				items.merge(plant.asItem(), 1, Integer::sum);
			}
			return new MaterialResolver.Needs(items, List.of(), Map.of());
		}
	}

	/** Infested stone and the like cost the plain block (the silverfish comes for free). */
	static final class InfestedHandler implements PlacementHandler {
		@Override
		public boolean canHandle(BlockState wanted) {
			return wanted.getBlock() instanceof InfestedBlock;
		}

		@Override
		public Cost cost(BlockState wanted) {
			return single(((InfestedBlock) wanted.getBlock()).getHostBlock().asItem(), 1);
		}
	}

	/** Any oxidation stage of the right copper block counts as done (MineColonies WeatheredCopperPlacementHandler). */
	static final class WeatheredCopperHandler implements PlacementHandler {
		@Override
		public boolean canHandle(BlockState wanted) {
			return wanted.getBlock() instanceof WeatheringCopper;
		}

		@Override
		public Cost cost(BlockState wanted) {
			return GeneralHandler.counted(wanted);
		}

		@Override
		public boolean matches(BlockState world, BlockState wanted) {
			return world.getBlock() instanceof WeatheringCopper
				&& WeatheringCopper.getFirst(world.getBlock()) == WeatheringCopper.getFirst(wanted.getBlock())
				&& GeneralHandler.sameProperties(world, wanted);
		}
	}

	/** Piston heads come with the extended piston. */
	static final class PistonHeadHandler implements PlacementHandler {
		@Override
		public boolean canHandle(BlockState wanted) {
			return wanted.is(Blocks.PISTON_HEAD);
		}

		@Override
		public Cost cost(BlockState wanted) {
			return MaterialResolver.FREE;
		}
	}

	/**
	 * Sand, gravel, anvils...: placed without the "on place" tick so they stay put like in the
	 * schematic even before their support is built (structurize places a support block instead).
	 */
	static final class FallingBlockHandler implements PlacementHandler {
		@Override
		public boolean canHandle(BlockState wanted) {
			return wanted.getBlock() instanceof Fallable;
		}

		@Override
		public Cost cost(BlockState wanted) {
			return GeneralHandler.counted(wanted);
		}

		@Override
		public Result place(ServerLevel level, BlockPos pos, BlockState wanted, int flags) {
			level.setBlock(pos, wanted, flags | Block.UPDATE_SKIP_ON_PLACE);
			return Result.PLACED;
		}
	}

	/** Everything else: the block's item, with counts for slabs, candles, sea pickles, snow layers... */
	static final class GeneralHandler implements PlacementHandler {
		private static final IntegerProperty[] COUNT_PROPERTIES = {
			BlockStateProperties.CANDLES, BlockStateProperties.PICKLES, BlockStateProperties.LAYERS,
			BlockStateProperties.EGGS, BlockStateProperties.FLOWER_AMOUNT, BlockStateProperties.SEGMENT_AMOUNT
		};
		/** Connection and power states the world computes itself (MineColonies GeneralBlockPlacementHandler). */
		private static final Set<String> CONNECTIONS = Set.of("north", "south", "east", "west", "up");

		@Override
		public boolean canHandle(BlockState wanted) {
			return true;
		}

		@Override
		public Cost cost(BlockState wanted) {
			return counted(wanted);
		}

		@Override
		public boolean matches(BlockState world, BlockState wanted) {
			return world.getBlock() == wanted.getBlock() && sameProperties(world, wanted);
		}

		static Cost counted(BlockState wanted) {
			int count = 1;
			if (wanted.hasProperty(BlockStateProperties.SLAB_TYPE) && wanted.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) {
				count = 2;
			}
			for (IntegerProperty property : COUNT_PROPERTIES) {
				if (wanted.hasProperty(property)) {
					count = wanted.getValue(property);
				}
			}
			return single(wanted.getBlock().asItem(), count);
		}

		/** Equal, ignoring states the game recomputes on its own (fence/wall/pane links, wire power, leaf distance). */
		static boolean sameProperties(BlockState world, BlockState wanted) {
			Block block = wanted.getBlock();
			if (block instanceof CrossCollisionBlock || block instanceof WallBlock) {
				return sameIgnoring(world, wanted, CONNECTIONS);
			}
			if (block == Blocks.REDSTONE_WIRE) {
				return sameIgnoring(world, wanted, Set.of("north", "south", "east", "west", "power"));
			}
			if (block instanceof LeavesBlock) {
				return sameIgnoring(world, wanted, Set.of("distance"));
			}
			return world == wanted;
		}
	}

	// --- helpers ------------------------------------------------------------------------

	static boolean sameIgnoring(BlockState a, BlockState b, Set<String> ignored) {
		if (a.getBlock() != b.getBlock()) {
			return false;
		}
		for (Property<?> property : b.getProperties()) {
			if (!ignored.contains(property.getName()) && (!a.hasProperty(property) || !a.getValue(property).equals(b.getValue(property)))) {
				return false;
			}
		}
		return true;
	}

	/** Lower half places both halves; the upper half only waits for that. */
	private static PlacementHandler.Result placeTwoHigh(ServerLevel level, BlockPos pos, BlockState wanted, int flags, PlacementHandler handler) {
		if (wanted.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			return handler.matches(level.getBlockState(pos), wanted) ? PlacementHandler.Result.PLACED : PlacementHandler.Result.DEFER;
		}
		level.setBlock(pos, wanted, flags);
		level.setBlock(pos.above(), wanted.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), flags);
		return PlacementHandler.Result.PLACED;
	}
}
