package com.bluetwinklez.aibuilders.build.schematic;

import com.mojang.datafixers.DataFixer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Reads Litematica {@code .litematic} files (already decompressed NBT).
 *
 * <pre>
 * root: Version, MinecraftDataVersion, Metadata{...},
 *   Regions{ name: { Position{x,y,z}, Size{x,y,z} (may be negative),
 *     BlockStatePalette[ {Name, Properties} ], BlockStates long[], TileEntities[], Entities[] } }
 * </pre>
 */
public final class LitematicReader {
	private LitematicReader() {
	}

	public static Schematic read(String name, CompoundTag root, HolderGetter<Block> blocks, @Nullable DataFixer fixer, long maxBlocks) {
		int dataVersion = root.getIntOr("MinecraftDataVersion", 0);
		PaletteFixer paletteFixer = new PaletteFixer(blocks, fixer, dataVersion);
		CompoundTag regionsTag = root.getCompoundOrEmpty("Regions");
		if (regionsTag.isEmpty()) {
			throw new SchematicException("No regions in litematic");
		}

		record RawRegion(String name, int minX, int minY, int minZ, Vec3i size, CompoundTag tag) {
		}
		List<RawRegion> raw = new ArrayList<>();
		long totalVolume = 0;
		for (String regionName : regionsTag.keySet()) {
			CompoundTag r = regionsTag.getCompoundOrEmpty(regionName);
			CompoundTag pos = r.getCompoundOrEmpty("Position");
			CompoundTag sz = r.getCompoundOrEmpty("Size");
			int px = pos.getIntOr("x", 0), py = pos.getIntOr("y", 0), pz = pos.getIntOr("z", 0);
			int sx = sz.getIntOr("x", 0), sy = sz.getIntOr("y", 0), sz2 = sz.getIntOr("z", 0);
			if (sx == 0 || sy == 0 || sz2 == 0) {
				continue;
			}
			// A negative size means the region extends from Position towards smaller coordinates.
			int minX = sx < 0 ? px + sx + 1 : px;
			int minY = sy < 0 ? py + sy + 1 : py;
			int minZ = sz2 < 0 ? pz + sz2 + 1 : pz;
			Vec3i size = new Vec3i(Math.abs(sx), Math.abs(sy), Math.abs(sz2));
			totalVolume += (long) size.getX() * size.getY() * size.getZ();
			raw.add(new RawRegion(regionName, minX, minY, minZ, size, r));
		}
		if (raw.isEmpty()) {
			throw new SchematicException("All regions are empty");
		}
		if (totalVolume > maxBlocks) {
			throw new SchematicException("Schematic volume " + totalVolume + " exceeds limit " + maxBlocks);
		}

		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (RawRegion r : raw) {
			minX = Math.min(minX, r.minX());
			minY = Math.min(minY, r.minY());
			minZ = Math.min(minZ, r.minZ());
			maxX = Math.max(maxX, r.minX() + r.size().getX());
			maxY = Math.max(maxY, r.minY() + r.size().getY());
			maxZ = Math.max(maxZ, r.minZ() + r.size().getZ());
		}

		List<Schematic.Region> regions = new ArrayList<>();
		int blockEntities = 0;
		int entities = 0;
		for (RawRegion r : raw) {
			ListTag paletteTag = r.tag().getListOrEmpty("BlockStatePalette");
			BlockState[] palette = new BlockState[paletteTag.size()];
			for (int i = 0; i < palette.length; i++) {
				palette[i] = paletteFixer.read(paletteTag.getCompoundOrEmpty(i));
			}
			long volume = (long) r.size().getX() * r.size().getY() * r.size().getZ();
			int bits = PackedBitArray.bitsForPaletteSize(palette.length);
			long[] data = r.tag().getLongArray("BlockStates").orElse(new long[0]);
			PackedBitArray array;
			try {
				array = new PackedBitArray(bits, volume, data);
			} catch (IllegalArgumentException e) {
				throw new SchematicException("Region '" + r.name() + "' has corrupt block data: " + e.getMessage());
			}
			regions.add(new Schematic.Region(
				r.name(), new Vec3i(r.minX() - minX, r.minY() - minY, r.minZ() - minZ), r.size(), palette, array
			));
			blockEntities += r.tag().getListOrEmpty("TileEntities").size();
			entities += r.tag().getListOrEmpty("Entities").size();
		}
		return new Schematic(name, new Vec3i(maxX - minX, maxY - minY, maxZ - minZ), regions, blockEntities, entities, paletteFixer.unknownCount());
	}
}
