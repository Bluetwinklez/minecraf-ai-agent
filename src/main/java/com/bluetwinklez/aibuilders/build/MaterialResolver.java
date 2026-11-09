package com.bluetwinklez.aibuilders.build;

import com.bluetwinklez.aibuilders.build.placement.PlacementHandlers;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What placing a schematic block costs and whether a world block already counts as built.
 * The per-block rules live in {@link PlacementHandlers} (adapted from structurize).
 */
public final class MaterialResolver {
	/** What placing one schematic block costs. */
	public sealed interface Cost {
	}

	/**
	 * Consumes {@code items}; needs each of {@code tools} in the inventory without using it up;
	 * gives {@code refunds} back afterwards (an empty bucket after pouring water, for example).
	 */
	public record Needs(Map<Item, Integer> items, List<Item> tools, Map<Item, Integer> refunds) implements Cost {
		public static Needs of(Item item, int count) {
			return new Needs(Map.of(item, count), List.of(), Map.of());
		}
	}

	/** Placed without consuming anything (second half of a door/bed, piston head...). */
	public record Free() implements Cost {
	}

	/** Cannot be placed in survival (portals, spawners, bedrock...). */
	public record Unplaceable() implements Cost {
	}

	public static final Free FREE = new Free();
	public static final Unplaceable UNPLACEABLE = new Unplaceable();

	private MaterialResolver() {
	}

	public static Cost cost(BlockState state) {
		return PlacementHandlers.get(state).cost(state);
	}

	/** True if {@code placed} already counts as the schematic's {@code wanted} block. */
	public static boolean matches(BlockState placed, BlockState wanted) {
		return placed == wanted || PlacementHandlers.get(wanted).matches(placed, wanted);
	}
}
