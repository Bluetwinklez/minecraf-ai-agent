package com.bluetwinklez.aibuilders.build.schematic;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Turns palette NBT entries into block states, upgrading entries saved by older game versions. */
public final class PaletteFixer {
	private final HolderGetter<Block> blocks;
	private final @Nullable DataFixer fixer;
	private final int fromVersion;
	private final int currentVersion = SharedConstants.getCurrentVersion().dataVersion().version();
	private int unknown;

	public PaletteFixer(HolderGetter<Block> blocks, @Nullable DataFixer fixer, int fromVersion) {
		this.blocks = blocks;
		this.fixer = fixer;
		this.fromVersion = fromVersion;
	}

	public BlockState read(CompoundTag entry) {
		CompoundTag tag = entry;
		if (fixer != null && fromVersion > 0 && fromVersion < currentVersion) {
			Tag updated = fixer.update(References.BLOCK_STATE, new Dynamic<>(NbtOps.INSTANCE, (Tag) entry), fromVersion, currentVersion).getValue();
			if (updated instanceof CompoundTag c) {
				tag = c;
			}
		}
		String name = tag.getStringOr("Name", "minecraft:air");
		Identifier id = Identifier.tryParse(name);
		if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
			unknown++;
			return Blocks.AIR.defaultBlockState();
		}
		return NbtUtils.readBlockState(blocks, tag);
	}

	/** Number of palette entries whose block does not exist in this game version. */
	public int unknownCount() {
		return unknown;
	}
}
