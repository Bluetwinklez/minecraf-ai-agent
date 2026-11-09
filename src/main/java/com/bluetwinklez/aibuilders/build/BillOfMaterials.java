package com.bluetwinklez.aibuilders.build;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Item counts needed for a set of blocks, plus blocks that cannot be placed in survival. */
public final class BillOfMaterials {
	private final Map<Item, Integer> items = new LinkedHashMap<>();
	private int unplaceable;

	public void add(MaterialResolver.Cost cost) {
		switch (cost) {
			case MaterialResolver.Needs needs -> items.merge(needs.item(), needs.count(), Integer::sum);
			case MaterialResolver.Unplaceable u -> unplaceable++;
			case MaterialResolver.Free f -> {
			}
		}
	}

	public void add(Item item, int count) {
		if (count > 0) {
			items.merge(item, count, Integer::sum);
		}
	}

	public Map<Item, Integer> items() {
		return items;
	}

	public int unplaceable() {
		return unplaceable;
	}

	public int totalItems() {
		return items.values().stream().mapToInt(Integer::intValue).sum();
	}

	/** Items still missing after taking {@code available} into account (largest first). */
	public List<Map.Entry<Item, Integer>> missing(Map<Item, Integer> available) {
		List<Map.Entry<Item, Integer>> result = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : items.entrySet()) {
			int lacking = e.getValue() - available.getOrDefault(e.getKey(), 0);
			if (lacking > 0) {
				result.add(Map.entry(e.getKey(), lacking));
			}
		}
		result.sort(Map.Entry.<Item, Integer>comparingByValue(Comparator.reverseOrder()));
		return result;
	}

	public List<Map.Entry<Item, Integer>> sorted() {
		return missing(Map.of());
	}

	/** "70x Oak Planks (1 stack + 6)". */
	public static MutableComponent line(Item item, int count) {
		ItemStack stack = new ItemStack(item);
		MutableComponent line = Component.literal(count + "x ").append(stack.getHoverName());
		int stackSize = stack.getMaxStackSize();
		if (stackSize > 1 && count >= stackSize) {
			int stacks = count / stackSize;
			int rest = count % stackSize;
			line.append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
				.append(rest == 0
					? Component.translatable("aibuilders.materials.stacks", stacks).withStyle(ChatFormatting.GRAY)
					: Component.translatable("aibuilders.materials.stacks_plus", stacks, rest).withStyle(ChatFormatting.GRAY))
				.append(Component.literal(")").withStyle(ChatFormatting.GRAY));
		}
		return line;
	}

	/** Chat lines for a list of entries, cut at {@code maxLines} with a "...and N more" line. */
	public static List<Component> lines(List<Map.Entry<Item, Integer>> entries, int maxLines) {
		List<Component> lines = new ArrayList<>();
		for (int i = 0; i < entries.size(); i++) {
			if (i >= maxLines) {
				lines.add(Component.translatable("aibuilders.materials.more", entries.size() - maxLines).withStyle(ChatFormatting.GRAY));
				break;
			}
			lines.add(Component.literal(" - ").append(line(entries.get(i).getKey(), entries.get(i).getValue())));
		}
		return lines;
	}
}
