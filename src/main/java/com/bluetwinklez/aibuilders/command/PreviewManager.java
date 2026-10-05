package com.bluetwinklez.aibuilders.command;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Draws schematic bounding boxes with particles for a few seconds. */
public final class PreviewManager {
	private static final int MAX_POINTS_PER_EDGE = 128;
	private static final List<Preview> ACTIVE = new ArrayList<>();

	private record Preview(ServerLevel level, BlockPos min, BlockPos max, long until) {
	}

	private PreviewManager() {
	}

	public static void show(ServerLevel level, BlockPos min, BlockPos max, int ticks) {
		ACTIVE.add(new Preview(level, min, max, level.getGameTime() + ticks));
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) {
			return;
		}
		Iterator<Preview> it = ACTIVE.iterator();
		while (it.hasNext()) {
			Preview p = it.next();
			if (p.level().getGameTime() > p.until()) {
				it.remove();
				continue;
			}
			draw(p);
		}
	}

	private static void draw(Preview p) {
		double x0 = p.min().getX(), y0 = p.min().getY(), z0 = p.min().getZ();
		double x1 = p.max().getX() + 1, y1 = p.max().getY() + 1, z1 = p.max().getZ() + 1;
		double[][] corners = {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}, {x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
		int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
		for (int[] e : edges) {
			double[] a = corners[e[0]], b = corners[e[1]];
			double length = Math.max(Math.abs(b[0] - a[0]), Math.max(Math.abs(b[1] - a[1]), Math.abs(b[2] - a[2])));
			int points = (int) Math.min(MAX_POINTS_PER_EDGE, Math.max(1, length));
			for (int i = 0; i <= points; i++) {
				double t = (double) i / points;
				p.level().sendParticles(ParticleTypes.END_ROD,
					a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t, 1, 0, 0, 0, 0);
			}
		}
	}
}
