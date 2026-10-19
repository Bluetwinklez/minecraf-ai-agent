package com.bluetwinklez.aibuilders.command;

import com.bluetwinklez.aibuilders.build.BuildTask;
import com.bluetwinklez.aibuilders.build.schematic.SchematicStore;
import com.bluetwinklez.aibuilders.config.AiBuildersConfig;
import com.bluetwinklez.aibuilders.entity.AgentNpc;
import com.bluetwinklez.aibuilders.entity.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Builder operations shared by {@code /aib} commands and the chat assistant, so both apply the
 * same permission rules. Must be called on the server thread.
 */
public final class BuilderActions {
	/** Who is asking: a player (or null for the console) and whether they are an operator. */
	public record Actor(MinecraftServer server, @Nullable ServerPlayer player, boolean gamemaster) {
		public static Actor of(ServerPlayer player) {
			return new Actor(player.level().getServer(), player, player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
		}

		public static Actor of(net.minecraft.commands.CommandSourceStack source) {
			return new Actor(source.getServer(), source.getPlayer(), source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
		}

		public boolean mayManage(AgentNpc npc) {
			return gamemaster || player != null && npc.isOwner(player);
		}
	}

	public record Result(boolean ok, Component message) {
		static Result ok(Component message) {
			return new Result(true, message);
		}

		static Result fail(Component message) {
			return new Result(false, message);
		}
	}

	private BuilderActions() {
	}

	public static List<AgentNpc> allNpcs(MinecraftServer server) {
		List<AgentNpc> result = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			result.addAll(level.getEntities(EntityTypeTest.forClass(AgentNpc.class), AgentNpc::isAlive));
		}
		return result;
	}

	public static Optional<AgentNpc> findNpc(MinecraftServer server, String name) {
		return allNpcs(server).stream().filter(n -> n.npcName().equalsIgnoreCase(name)).findFirst();
	}

	/** NPCs this actor may manage. */
	public static List<AgentNpc> managedNpcs(Actor actor) {
		return allNpcs(actor.server()).stream().filter(actor::mayManage).toList();
	}

	private static Result npcOr(Actor actor, String name, java.util.function.Function<AgentNpc, Result> action) {
		Optional<AgentNpc> npc = findNpc(actor.server(), name);
		if (npc.isEmpty()) {
			return Result.fail(Component.translatable("aibuilders.command.npc_not_found", name));
		}
		if (!actor.mayManage(npc.get())) {
			return Result.fail(Component.translatable("aibuilders.npc.not_owner"));
		}
		return action.apply(npc.get());
	}

	public static Result spawn(Actor actor, String name, String skinPlayer) {
		ServerPlayer player = actor.player();
		if (player == null) {
			return Result.fail(Component.translatable("aibuilders.command.player_only"));
		}
		if (name.isBlank() || !name.matches("[A-Za-z0-9_çğıöşüÇĞİÖŞÜ]{1,16}")) {
			return Result.fail(Component.translatable("aibuilders.command.bad_name", name));
		}
		if (findNpc(actor.server(), name).isPresent()) {
			return Result.fail(Component.translatable("aibuilders.command.name_taken", name));
		}
		int limit = AiBuildersConfig.get().maxNpcsPerPlayer;
		if (!actor.gamemaster() && allNpcs(actor.server()).stream().filter(n -> n.isOwner(player)).count() >= limit) {
			return Result.fail(Component.translatable("aibuilders.command.limit", limit));
		}
		ServerLevel level = player.level();
		AgentNpc npc = ModEntities.BUILDER.create(level, EntitySpawnReason.COMMAND);
		if (npc == null) {
			return Result.fail(Component.literal("spawn failed"));
		}
		npc.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
		npc.setup(name, skinPlayer, player.getUUID());
		level.addFreshEntity(npc);
		return Result.ok(Component.translatable("aibuilders.command.spawned", name));
	}

	public static Result build(Actor actor, String npcName, String schematic, BlockPos origin, Rotation rotation, Mirror mirror) {
		return npcOr(actor, npcName, npc -> {
			if (SchematicStore.find(schematic).isEmpty()) {
				return Result.fail(Component.translatable("aibuilders.command.schematic_not_found", schematic));
			}
			if (actor.player() != null && npc.level() != actor.player().level()) {
				return Result.fail(Component.translatable("aibuilders.command.other_dimension", npc.npcName()));
			}
			npc.taskRunner().start(new BuildTask(schematic, origin, mirror, rotation), npc, (ServerLevel) npc.level());
			return Result.ok(Component.translatable("aibuilders.command.build_started", npc.npcName(), schematic, origin.toShortString()));
		});
	}

	public static Result stop(Actor actor, String npcName) {
		return npcOr(actor, npcName, npc -> {
			npc.taskRunner().stop(npc, (ServerLevel) npc.level());
			return Result.ok(Component.translatable("aibuilders.command.stopped", npc.npcName()));
		});
	}

	public static Result setPaused(Actor actor, String npcName, boolean paused) {
		return npcOr(actor, npcName, npc -> {
			if (!npc.taskRunner().hasTask()) {
				return Result.fail(Component.translatable("aibuilders.npc.idle"));
			}
			npc.taskRunner().setPaused(paused);
			if (paused) {
				npc.getNavigation().stop();
			}
			return Result.ok(Component.translatable(paused ? "aibuilders.command.paused" : "aibuilders.command.resumed", npc.npcName()));
		});
	}

	public static Result remove(Actor actor, String npcName) {
		return npcOr(actor, npcName, npc -> {
			ServerLevel level = (ServerLevel) npc.level();
			npc.taskRunner().stop(npc, level);
			npc.dropInventory(level);
			npc.discard();
			return Result.ok(Component.translatable("aibuilders.command.removed", npc.npcName()));
		});
	}

	/** Plain-English status for the chat model (it rewrites it in the player's language). */
	public static String describe(AgentNpc npc) {
		Optional<BuildTask> build = npc.buildTask();
		if (build.isEmpty()) {
			return npc.npcName() + " is idle";
		}
		BuildTask task = build.get();
		String state = npc.taskRunner().isPaused() ? "paused" : task.isWaiting() ? "waiting for materials" : "working";
		return npc.npcName() + " is building '" + task.schematicName() + "', " + Math.round(task.progress() * 100) + "% done, " + state;
	}

	/** The block in front of what the player looks at, or their position if they look at the sky. */
	public static BlockPos lookingPos(ServerPlayer player) {
		HitResult hit = player.pick(32.0, 1.0F, false);
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			return blockHit.getBlockPos().relative(blockHit.getDirection());
		}
		return player.blockPosition();
	}

	public static @Nullable ServerPlayer player(MinecraftServer server, UUID id) {
		return server.getPlayerList().getPlayer(id);
	}
}
