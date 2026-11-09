package com.bluetwinklez.aibuilders.build;

import com.bluetwinklez.aibuilders.entity.AgentNpc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.jspecify.annotations.Nullable;

/** The NPC inventory plus chests, barrels and shulker boxes around it. */
public final class MaterialInventory {
	private MaterialInventory() {
	}

	/** Loaded chests/barrels/shulker boxes within {@code radius} blocks of {@code center}. */
	public static List<BlockPos> containersAround(ServerLevel level, BlockPos center, int radius) {
		List<BlockPos> result = new ArrayList<>();
		int minCx = SectionPos.blockToSectionCoord(center.getX() - radius);
		int maxCx = SectionPos.blockToSectionCoord(center.getX() + radius);
		int minCz = SectionPos.blockToSectionCoord(center.getZ() - radius);
		int maxCz = SectionPos.blockToSectionCoord(center.getZ() + radius);
		long r2 = (long) radius * radius;
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
					if (isStorage(be) && be.getBlockPos().distSqr(center) <= r2) {
						result.add(be.getBlockPos().immutable());
					}
				}
			}
		}
		return result;
	}

	private static boolean isStorage(BlockEntity be) {
		return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity;
	}

	public static @Nullable Container containerAt(ServerLevel level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		return be != null && isStorage(be) ? (Container) be : null;
	}

	public static void count(Container container, Map<Item, Integer> into) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty()) {
				into.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
	}

	/** Everything the NPC could use right now: own inventory + nearby containers. */
	public static Map<Item, Integer> available(ServerLevel level, AgentNpc npc, List<BlockPos> containers) {
		Map<Item, Integer> counts = new HashMap<>();
		count(npc.getInventory(), counts);
		for (BlockPos pos : containers) {
			Container c = containerAt(level, pos);
			if (c != null) {
				count(c, counts);
			}
		}
		return counts;
	}

	public static int countIn(Container container, Item item) {
		int n = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** Removes up to {@code amount} of {@code item}; returns how many were removed. */
	public static int take(Container container, Item item, int amount) {
		int taken = 0;
		for (int i = 0; i < container.getContainerSize() && taken < amount; i++) {
			ItemStack stack = container.getItem(i);
			if (stack.is(item)) {
				int n = Math.min(stack.getCount(), amount - taken);
				stack.shrink(n);
				taken += n;
			}
		}
		if (taken > 0) {
			container.setChanged();
		}
		return taken;
	}

	/**
	 * Moves items from {@code from} into the NPC inventory, at most {@code wanted.get(item)} of
	 * each, until the inventory is full. Returns the number of items moved.
	 */
	public static int transfer(Container from, SimpleContainer to, Map<Item, Integer> wanted) {
		int moved = 0;
		for (int i = 0; i < from.getContainerSize(); i++) {
			ItemStack stack = from.getItem(i);
			Item item = stack.getItem();
			Integer want = stack.isEmpty() ? null : wanted.get(item);
			if (want == null || want <= 0) {
				continue;
			}
			ItemStack part = stack.copyWithCount(Math.min(want, stack.getCount()));
			ItemStack rest = to.addItem(part);
			int n = part.getCount() - rest.getCount();
			if (n <= 0) {
				break;
			}
			stack.shrink(n);
			wanted.merge(item, -n, Integer::sum);
			moved += n;
		}
		if (moved > 0) {
			from.setChanged();
		}
		return moved;
	}
}
