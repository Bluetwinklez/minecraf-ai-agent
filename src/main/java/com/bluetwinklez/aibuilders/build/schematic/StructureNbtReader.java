package com.bluetwinklez.aibuilders.build.schematic;

import com.mojang.datafixers.DataFixer;
import java.util.List;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Reads vanilla structure block files ({@code .nbt}: size, palette/palettes, blocks[{pos, state}]). */
public final class StructureNbtReader {
	private StructureNbtReader() {
	}

	public static Schematic read(String name, CompoundTag root, HolderGetter<Block> blocks, @Nullable DataFixer fixer, long maxBlocks) {
		ListTag sizeTag = root.getListOrEmpty("size");
		if (sizeTag.size() != 3) {
			throw new SchematicException("Missing size");
		}
		Vec3i size = new Vec3i(sizeTag.getInt(0).orElse(0), sizeTag.getInt(1).orElse(0), sizeTag.getInt(2).orElse(0));
		long volume = (long) size.getX() * size.getY() * size.getZ();
		if (volume <= 0) {
			throw new SchematicException("Empty structure");
		}
		if (volume > maxBlocks) {
			throw new SchematicException("Schematic volume " + volume + " exceeds limit " + maxBlocks);
		}

		ListTag paletteTag = root.getList("palette").orElseGet(() -> root.getListOrEmpty("palettes").getListOrEmpty(0));
		PaletteFixer paletteFixer = new PaletteFixer(blocks, fixer, root.getIntOr("DataVersion", 0));
		// Index 0 is reserved for "not present" (air); palette entry i becomes i + 1.
		BlockState[] palette = new BlockState[paletteTag.size() + 1];
		palette[0] = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		for (int i = 0; i < paletteTag.size(); i++) {
			palette[i + 1] = paletteFixer.read(paletteTag.getCompoundOrEmpty(i));
		}

		PackedBitArray array = new PackedBitArray(PackedBitArray.bitsForPaletteSize(palette.length), volume);
		ListTag blocksTag = root.getListOrEmpty("blocks");
		int blockEntities = 0;
		for (int i = 0; i < blocksTag.size(); i++) {
			CompoundTag b = blocksTag.getCompoundOrEmpty(i);
			ListTag pos = b.getListOrEmpty("pos");
			int x = pos.getInt(0).orElse(-1), y = pos.getInt(1).orElse(-1), z = pos.getInt(2).orElse(-1);
			int state = b.getIntOr("state", -1);
			if (x < 0 || y < 0 || z < 0 || x >= size.getX() || y >= size.getY() || z >= size.getZ() || state < 0 || state >= paletteTag.size()) {
				continue;
			}
			array.set(((long) y * size.getZ() + z) * size.getX() + x, state + 1);
			if (b.contains("nbt")) {
				blockEntities++;
			}
		}
		Schematic.Region region = new Schematic.Region("main", Vec3i.ZERO, size, palette, array);
		return new Schematic(name, size, List.of(region), blockEntities, root.getListOrEmpty("entities").size(), paletteFixer.unknownCount());
	}
}
