package com.bluetwinklez.aibuilders.gametest;

import com.bluetwinklez.aibuilders.build.MaterialInventory;
import com.bluetwinklez.aibuilders.config.AiBuildersConfig;
import com.bluetwinklez.aibuilders.entity.AgentNpc;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Placement handler rules (adapted from structurize) in a real survival build. */
public class PlacementGameTests {
	private static final String ARENA = "aibuilders-gametest:arena";
	// By id: 26.3 moved colored beds into a ColorCollection, 26.2 still has Blocks.RED_BED.
	private static final net.minecraft.world.level.block.Block RED_BED =
		net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("red_bed"));
	private static final net.minecraft.world.item.Item RED_BED_ITEM = RED_BED.asItem();

	/**
	 * Door, bed, sunflower, potted poppy, double slab, floating sand, water and a fence: each costs
	 * the right items, multi-block halves are placed together and the empty bucket comes back.
	 */
	@GameTest(structure = ARENA, maxTicks = 800)
	public void survivalPlacesSpecialBlocksWithCorrectCosts(GameTestHelper helper) {
		BuilderGameTests.setUp(helper);
		Map<BlockPos, BlockState> blocks = new HashMap<>();
		BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
		blocks.put(new BlockPos(0, 0, 0), door);
		blocks.put(new BlockPos(0, 1, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		BlockState bed = RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
		blocks.put(new BlockPos(2, 0, 0), bed);
		blocks.put(new BlockPos(3, 0, 0), bed.setValue(BedBlock.PART, BedPart.HEAD));
		BlockState sunflower = Blocks.SUNFLOWER.defaultBlockState();
		blocks.put(new BlockPos(5, 0, 0), sunflower);
		blocks.put(new BlockPos(5, 1, 0), sunflower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
		blocks.put(new BlockPos(7, 0, 0), Blocks.POTTED_POPPY.defaultBlockState());
		blocks.put(new BlockPos(8, 0, 0), Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
		blocks.put(new BlockPos(9, 1, 0), Blocks.SAND.defaultBlockState());
		blocks.put(new BlockPos(10, 0, 0), Blocks.WATER.defaultBlockState());
		// The schematic's fence has no connections; the placed one connects to the slab. Still counts as done.
		blocks.put(new BlockPos(8, 1, 0), Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true));
		BuilderGameTests.writeStructurePublic("gt_special", 11, 2, 1, blocks);

		ChestBlockEntity chest = BuilderGameTests.chestWith(helper, new BlockPos(3, 1, 5),
			new ItemStack(Items.OAK_DOOR), new ItemStack(RED_BED_ITEM), new ItemStack(Items.SUNFLOWER), new ItemStack(Items.FLOWER_POT),
			new ItemStack(Items.POPPY), new ItemStack(Items.OAK_SLAB, 2), new ItemStack(Items.SAND), new ItemStack(Items.WATER_BUCKET),
			new ItemStack(Items.OAK_FENCE));
		AgentNpc npc = BuilderGameTests.spawnNpc(helper, 5, 4);
		BuilderGameTests.start(helper, npc, "gt_special", new BlockPos(2, 1, 8), Rotation.NONE, AiBuildersConfig.BuildMode.SURVIVAL);
		helper.succeedWhen(() -> {
			helper.assertFalse(npc.taskRunner().hasTask(), "task should be finished");
			helper.assertBlockPresent(Blocks.OAK_DOOR, new BlockPos(2, 1, 8));
			helper.assertBlockPresent(Blocks.OAK_DOOR, new BlockPos(2, 2, 8));
			helper.assertBlockPresent(RED_BED, new BlockPos(5, 1, 8));
			helper.assertBlockPresent(Blocks.SUNFLOWER, new BlockPos(7, 2, 8));
			helper.assertBlockPresent(Blocks.POTTED_POPPY, new BlockPos(9, 1, 8));
			helper.assertBlockPresent(Blocks.SAND, new BlockPos(11, 2, 8));
			helper.assertBlockPresent(Blocks.WATER, new BlockPos(12, 1, 8));
			helper.assertBlockPresent(Blocks.OAK_FENCE, new BlockPos(10, 2, 8));
			helper.assertBlockState(new BlockPos(10, 1, 8), s -> s.is(Blocks.OAK_SLAB) && s.getValue(SlabBlock.TYPE) == SlabType.DOUBLE,
				s -> net.minecraft.network.chat.Component.literal("double slab expected, got " + s));
			// Everything was used up exactly; only the empty bucket is left (refund).
			for (var item : new net.minecraft.world.item.Item[] {Items.OAK_DOOR, RED_BED_ITEM, Items.SUNFLOWER, Items.FLOWER_POT,
				Items.POPPY, Items.OAK_SLAB, Items.SAND, Items.WATER_BUCKET, Items.OAK_FENCE}) {
				int left = MaterialInventory.countIn(chest, item) + MaterialInventory.countIn(npc.getInventory(), item);
				helper.assertValueEqual(left, 0, item.toString() + " left");
			}
			helper.assertValueEqual(MaterialInventory.countIn(npc.getInventory(), Items.BUCKET), 1, "empty bucket returned");
		});
	}
}
