package com.bluetwinklez.aibuilders.gametest;

import com.bluetwinklez.aibuilders.build.BuildTask;
import com.bluetwinklez.aibuilders.build.MaterialInventory;
import com.bluetwinklez.aibuilders.build.schematic.PackedBitArray;
import com.bluetwinklez.aibuilders.config.AiBuildersConfig;
import com.bluetwinklez.aibuilders.entity.AgentNpc;
import com.bluetwinklez.aibuilders.entity.ModEntities;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BuilderGameTests {
	private static final String ARENA = "aibuilders-gametest:arena";

	// --- helpers ------------------------------------------------------------------

	private static void setUp(GameTestHelper helper) {
		AiBuildersConfig config = AiBuildersConfig.get();
		config.blocksPerSecond = 40;
		config.materialRecheckSeconds = 1;
		config.showBossBar = false;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static AgentNpc spawnNpc(GameTestHelper helper, int x, int z) {
		AgentNpc npc = helper.spawn(ModEntities.BUILDER, new BlockPos(x, 1, z));
		npc.setup("Tester", "Steve", null);
		return npc;
	}

	private static void start(GameTestHelper helper, AgentNpc npc, String schematic, BlockPos rel, Rotation rotation, AiBuildersConfig.BuildMode mode) {
		npc.taskRunner().start(new BuildTask(schematic, helper.absolutePos(rel), Mirror.NONE, rotation, mode), npc, helper.getLevel());
	}

	private static Path schematicDir() {
		Path dir = AiBuildersConfig.configDir().resolve("schematics");
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return dir;
	}

	static void writeStructurePublic(String name, int sx, int sy, int sz, Map<BlockPos, BlockState> blocks) {
		writeStructure(name, sx, sy, sz, blocks);
	}

	/** Writes a vanilla structure file: blocks maps "x,y,z" to a state. */
	private static void writeStructure(String name, int sx, int sy, int sz, Map<BlockPos, BlockState> blocks) {
		CompoundTag root = new CompoundTag();
		root.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
		ListTag size = new ListTag();
		size.add(IntTag.valueOf(sx));
		size.add(IntTag.valueOf(sy));
		size.add(IntTag.valueOf(sz));
		root.put("size", size);
		List<BlockState> palette = blocks.values().stream().distinct().toList();
		ListTag paletteTag = new ListTag();
		palette.forEach(s -> paletteTag.add(NbtUtils.writeBlockState(s)));
		root.put("palette", paletteTag);
		ListTag blockList = new ListTag();
		blocks.forEach((pos, state) -> {
			CompoundTag b = new CompoundTag();
			ListTag p = new ListTag();
			p.add(IntTag.valueOf(pos.getX()));
			p.add(IntTag.valueOf(pos.getY()));
			p.add(IntTag.valueOf(pos.getZ()));
			b.put("pos", p);
			b.putInt("state", palette.indexOf(state));
			blockList.add(b);
		});
		root.put("blocks", blockList);
		root.put("entities", new ListTag());
		write(name + ".nbt", root);
	}

	private static CompoundTag litematicRegion(int px, int py, int pz, int sx, int sy, int sz, List<BlockState> palette, int[] ids) {
		CompoundTag region = new CompoundTag();
		CompoundTag pos = new CompoundTag();
		pos.putInt("x", px);
		pos.putInt("y", py);
		pos.putInt("z", pz);
		region.put("Position", pos);
		CompoundTag size = new CompoundTag();
		size.putInt("x", sx);
		size.putInt("y", sy);
		size.putInt("z", sz);
		region.put("Size", size);
		ListTag paletteTag = new ListTag();
		palette.forEach(s -> paletteTag.add(NbtUtils.writeBlockState(s)));
		region.put("BlockStatePalette", paletteTag);
		PackedBitArray bits = new PackedBitArray(PackedBitArray.bitsForPaletteSize(palette.size()), ids.length);
		for (int i = 0; i < ids.length; i++) {
			bits.set(i, ids[i]);
		}
		region.putLongArray("BlockStates", bits.data());
		region.put("TileEntities", new ListTag());
		region.put("Entities", new ListTag());
		return region;
	}

	private static void write(String file, CompoundTag root) {
		try {
			NbtIo.writeCompressed(root, schematicDir().resolve(file));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static ChestBlockEntity chestWith(GameTestHelper helper, BlockPos rel, ItemStack... stacks) {
		helper.setBlock(rel, Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
		for (int i = 0; i < stacks.length; i++) {
			chest.setItem(i, stacks[i]);
		}
		return chest;
	}

	// --- tests ----------------------------------------------------------------------

	/** Litematic with two regions, one with negative size; creative mode places everything. */
	@GameTest(structure = ARENA, maxTicks = 400)
	public void creativeBuildsMultiRegionLitematic(GameTestHelper helper) {
		setUp(helper);
		List<BlockState> palette = List.of(Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.GLASS.defaultBlockState());
		int[] cube = new int[27];
		java.util.Arrays.fill(cube, 1);
		cube[13] = 2; // center (1,1,1) is glass
		CompoundTag regions = new CompoundTag();
		// Position (2,0,2) with size (-3,3,-3) covers x/z 0..2.
		regions.put("cube", litematicRegion(2, 0, 2, -3, 3, -3, palette, cube));
		regions.put("top", litematicRegion(0, 3, 0, 1, 1, 1, List.of(Blocks.AIR.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState()), new int[] {1}));
		CompoundTag root = new CompoundTag();
		root.putInt("Version", 7);
		root.putInt("MinecraftDataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
		root.put("Metadata", new CompoundTag());
		root.put("Regions", regions);
		write("gt_litematic.litematic", root);

		AgentNpc npc = spawnNpc(helper, 2, 2);
		start(helper, npc, "gt_litematic", new BlockPos(6, 1, 6), Rotation.NONE, AiBuildersConfig.BuildMode.CREATIVE);
		helper.succeedWhen(() -> {
			for (int x = 0; x < 3; x++) {
				for (int y = 0; y < 3; y++) {
					for (int z = 0; z < 3; z++) {
						helper.assertBlockPresent(x == 1 && y == 1 && z == 1 ? Blocks.GLASS : Blocks.STONE, new BlockPos(6 + x, 1 + y, 6 + z));
					}
				}
			}
			helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(6, 4, 6));
			helper.assertFalse(npc.taskRunner().hasTask(), "task should be finished");
		});
	}

	/** Clockwise rotation moves the +X neighbour to +Z. */
	@GameTest(structure = ARENA, maxTicks = 200)
	public void rotationIsApplied(GameTestHelper helper) {
		setUp(helper);
		writeStructure("gt_rotate", 2, 1, 1, Map.of(
			new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState(),
			new BlockPos(1, 0, 0), Blocks.GLASS.defaultBlockState()
		));
		AgentNpc npc = spawnNpc(helper, 3, 3);
		start(helper, npc, "gt_rotate", new BlockPos(6, 1, 6), Rotation.CLOCKWISE_90, AiBuildersConfig.BuildMode.CREATIVE);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.STONE, new BlockPos(6, 1, 6));
			helper.assertBlockPresent(Blocks.GLASS, new BlockPos(6, 1, 7));
		});
	}

	/** Survival without materials: the NPC waits and knows exactly what is missing. */
	@GameTest(structure = ARENA, maxTicks = 200)
	public void survivalWaitsAndListsMissingMaterials(GameTestHelper helper) {
		setUp(helper);
		writeStructure("gt_missing", 2, 1, 1, Map.of(
			new BlockPos(0, 0, 0), Blocks.OAK_PLANKS.defaultBlockState(),
			new BlockPos(1, 0, 0), Blocks.OAK_PLANKS.defaultBlockState()
		));
		AgentNpc npc = spawnNpc(helper, 3, 3);
		start(helper, npc, "gt_missing", new BlockPos(6, 1, 6), Rotation.NONE, AiBuildersConfig.BuildMode.SURVIVAL);
		helper.succeedWhen(() -> {
			BuildTask task = npc.buildTask().orElseThrow(() -> helper.assertionException("no build task"));
			helper.assertTrue(task.isWaiting(), "NPC should wait for materials");
			Map<Item, Integer> needed = task.remaining(helper.getLevel()).items();
			helper.assertValueEqual(needed.get(Items.OAK_PLANKS), 2, "planks needed");
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(6, 1, 6));
		});
	}

	/** Survival with a chest nearby: the NPC fetches what it needs and finishes. */
	@GameTest(structure = ARENA, maxTicks = 600)
	public void survivalTakesMaterialsFromChest(GameTestHelper helper) {
		setUp(helper);
		writeStructure("gt_chest", 3, 1, 3, Map.of(
			new BlockPos(0, 0, 0), Blocks.COBBLESTONE.defaultBlockState(), new BlockPos(1, 0, 0), Blocks.COBBLESTONE.defaultBlockState(),
			new BlockPos(2, 0, 0), Blocks.COBBLESTONE.defaultBlockState(), new BlockPos(0, 0, 1), Blocks.COBBLESTONE.defaultBlockState(),
			new BlockPos(1, 0, 1), Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.DOUBLE),
			new BlockPos(2, 0, 1), Blocks.COBBLESTONE.defaultBlockState(), new BlockPos(0, 0, 2), Blocks.COBBLESTONE.defaultBlockState(),
			new BlockPos(1, 0, 2), Blocks.COBBLESTONE.defaultBlockState(), new BlockPos(2, 0, 2), Blocks.COBBLESTONE.defaultBlockState()
		));
		ChestBlockEntity chest = chestWith(helper, new BlockPos(2, 1, 8), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.OAK_SLAB, 2));
		AgentNpc npc = spawnNpc(helper, 2, 6);
		start(helper, npc, "gt_chest", new BlockPos(6, 1, 6), Rotation.NONE, AiBuildersConfig.BuildMode.SURVIVAL);
		helper.succeedWhen(() -> {
			helper.assertFalse(npc.taskRunner().hasTask(), "task should be finished");
			helper.assertBlockPresent(Blocks.OAK_SLAB, new BlockPos(7, 1, 7));
			helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(8, 1, 8));
			// 8 cobblestone and a double slab (2 slabs) were used.
			int left = MaterialInventory.countIn(chest, Items.COBBLESTONE) + MaterialInventory.countIn(npc.getInventory(), Items.COBBLESTONE);
			helper.assertValueEqual(left, 56, "cobblestone left");
			int slabsLeft = MaterialInventory.countIn(chest, Items.OAK_SLAB) + MaterialInventory.countIn(npc.getInventory(), Items.OAK_SLAB);
			helper.assertValueEqual(slabsLeft, 0, "slabs left");
		});
	}

	/** A tower taller than reach: scaffolding goes up, the tower is finished, scaffolding comes back. */
	@GameTest(structure = ARENA, maxTicks = 1200)
	public void survivalUsesScaffoldingForTallTower(GameTestHelper helper) {
		setUp(helper);
		java.util.Map<BlockPos, BlockState> tower = new java.util.HashMap<>();
		for (int y = 0; y < 12; y++) {
			tower.put(new BlockPos(0, y, 0), Blocks.STONE_BRICKS.defaultBlockState());
		}
		writeStructure("gt_tower", 1, 12, 1, tower);
		chestWith(helper, new BlockPos(3, 1, 3), new ItemStack(Items.STONE_BRICKS, 16), new ItemStack(Items.SCAFFOLDING, 32));
		AgentNpc npc = spawnNpc(helper, 4, 4);
		start(helper, npc, "gt_tower", new BlockPos(8, 1, 8), Rotation.NONE, AiBuildersConfig.BuildMode.SURVIVAL);
		helper.succeedWhen(() -> {
			helper.assertFalse(npc.taskRunner().hasTask(), "task should be finished");
			for (int y = 1; y <= 12; y++) {
				helper.assertBlockPresent(Blocks.STONE_BRICKS, new BlockPos(8, y, 8));
			}
			for (int x = 6; x <= 10; x++) {
				for (int z = 6; z <= 10; z++) {
					for (int y = 1; y <= 14; y++) {
						BlockPos p = new BlockPos(x, y, z);
						helper.assertFalse(helper.getBlockState(p).is(Blocks.SCAFFOLDING), "scaffolding left at " + p);
					}
				}
			}
			helper.assertTrue(MaterialInventory.countIn(npc.getInventory(), Items.SCAFFOLDING) > 0, "scaffolding returned to NPC");
		});
	}
}
