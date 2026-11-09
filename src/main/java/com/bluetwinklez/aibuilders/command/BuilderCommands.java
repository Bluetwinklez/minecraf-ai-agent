package com.bluetwinklez.aibuilders.command;

import com.bluetwinklez.aibuilders.build.BillOfMaterials;
import com.bluetwinklez.aibuilders.build.BuildTask;
import com.bluetwinklez.aibuilders.build.MaterialResolver;
import com.bluetwinklez.aibuilders.build.schematic.Schematic;
import com.bluetwinklez.aibuilders.build.schematic.SchematicStore;
import com.bluetwinklez.aibuilders.entity.AgentNpc;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.TemplateMirrorArgument;
import net.minecraft.commands.arguments.TemplateRotationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.jspecify.annotations.Nullable;

/** {@code /aibuilders} (short: {@code /aib}). */
public final class BuilderCommands {
	private static final int MAX_LIST_LINES = 200;

	private BuilderCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("aibuilders")
			.then(Commands.literal("spawn")
				.then(Commands.argument("name", StringArgumentType.word())
					.executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "name")))
					.then(Commands.argument("skinPlayer", StringArgumentType.word())
						.executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "skinPlayer"))))))
			.then(npcCommand("remove", BuilderCommands::remove))
			.then(npcCommand("stop", BuilderCommands::stop))
			.then(npcCommand("pause", (ctx, npc) -> setPaused(ctx, npc, true)))
			.then(npcCommand("resume", (ctx, npc) -> setPaused(ctx, npc, false)))
			.then(npcCommand("status", BuilderCommands::status))
			.then(Commands.literal("build")
				.then(npcArgument()
					.then(schematicArgument()
						.executes(ctx -> build(ctx, null, Rotation.NONE, Mirror.NONE))
						.then(Commands.argument("pos", BlockPosArgument.blockPos())
							.executes(ctx -> build(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos"), Rotation.NONE, Mirror.NONE))
							.then(Commands.argument("rotation", TemplateRotationArgument.templateRotation())
								.executes(ctx -> build(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos"), TemplateRotationArgument.getRotation(ctx, "rotation"), Mirror.NONE))
								.then(Commands.argument("mirror", TemplateMirrorArgument.templateMirror())
									.executes(ctx -> build(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
										TemplateRotationArgument.getRotation(ctx, "rotation"), TemplateMirrorArgument.getMirror(ctx, "mirror")))))))))
			.then(Commands.literal("preview")
				.then(schematicArgument()
					.executes(ctx -> preview(ctx, null, Rotation.NONE, Mirror.NONE))
					.then(Commands.argument("pos", BlockPosArgument.blockPos())
						.executes(ctx -> preview(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos"), Rotation.NONE, Mirror.NONE))
						.then(Commands.argument("rotation", TemplateRotationArgument.templateRotation())
							.executes(ctx -> preview(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos"), TemplateRotationArgument.getRotation(ctx, "rotation"), Mirror.NONE))
							.then(Commands.argument("mirror", TemplateMirrorArgument.templateMirror())
								.executes(ctx -> preview(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
									TemplateRotationArgument.getRotation(ctx, "rotation"), TemplateMirrorArgument.getMirror(ctx, "mirror"))))))))
			.then(Commands.literal("info").then(schematicArgument().executes(BuilderCommands::info)))
			.then(Commands.literal("materials")
				.then(Commands.argument("target", StringArgumentType.string())
					.suggests(BuilderCommands::suggestNpcsAndSchematics)
					.executes(BuilderCommands::materials)))
			.then(Commands.literal("schematics").executes(BuilderCommands::listSchematics));
		var node = dispatcher.register(root);
		dispatcher.register(Commands.literal("aib").redirect(node));
	}

	// --- argument helpers -------------------------------------------------------

	@FunctionalInterface
	private interface NpcAction {
		int run(CommandContext<CommandSourceStack> ctx, AgentNpc npc) throws CommandSyntaxException;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> npcCommand(String name, NpcAction action) {
		return Commands.literal(name).then(npcArgument().executes(ctx -> {
			AgentNpc npc = managedNpc(ctx);
			return npc == null ? 0 : action.run(ctx, npc);
		}));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> npcArgument() {
		return Commands.argument("npc", StringArgumentType.word()).suggests(BuilderCommands::suggestNpcs);
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> schematicArgument() {
		return Commands.argument("schematic", StringArgumentType.string()).suggests(BuilderCommands::suggestSchematics);
	}

	private static String quoteIfNeeded(String s) {
		for (char c : s.toCharArray()) {
			if (!StringReader.isAllowedInUnquotedString(c)) {
				return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
			}
		}
		return s;
	}

	private static CompletableFuture<Suggestions> suggestNpcs(
		CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder
	) {
		return SharedSuggestionProvider.suggest(allNpcs(ctx.getSource()).stream().map(AgentNpc::npcName), builder);
	}

	private static CompletableFuture<Suggestions> suggestSchematics(
		CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder
	) {
		return SharedSuggestionProvider.suggest(SchematicStore.list().stream().map(BuilderCommands::quoteIfNeeded), builder);
	}

	private static CompletableFuture<Suggestions> suggestNpcsAndSchematics(
		CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder
	) {
		List<String> all = new ArrayList<>();
		allNpcs(ctx.getSource()).forEach(n -> all.add(n.npcName()));
		SchematicStore.list().forEach(s -> all.add(quoteIfNeeded(s)));
		return SharedSuggestionProvider.suggest(all, builder);
	}

	private static List<AgentNpc> allNpcs(CommandSourceStack source) {
		return BuilderActions.allNpcs(source.getServer());
	}

	private static Optional<AgentNpc> findNpc(CommandSourceStack source, String name) {
		return BuilderActions.findNpc(source.getServer(), name);
	}

	/** Sends the result to the command source; returns 1 on success, 0 on failure. */
	private static int send(CommandSourceStack source, BuilderActions.Result result) {
		if (result.ok()) {
			source.sendSuccess(result::message, false);
			return 1;
		}
		source.sendFailure(result.message());
		return 0;
	}

	/** The named NPC if the command source may control it; otherwise sends an error and returns null. */
	private static @Nullable AgentNpc managedNpc(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "npc");
		Optional<AgentNpc> npc = findNpc(source, name);
		if (npc.isEmpty()) {
			source.sendFailure(Component.translatable("aibuilders.command.npc_not_found", name));
			return null;
		}
		if (!BuilderActions.Actor.of(source).mayManage(npc.get())) {
			source.sendFailure(Component.translatable("aibuilders.npc.not_owner"));
			return null;
		}
		return npc.get();
	}

	// --- commands ----------------------------------------------------------------

	private static int spawn(CommandContext<CommandSourceStack> ctx, String skinPlayer) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		source.getPlayerOrException();
		return send(source, BuilderActions.spawn(BuilderActions.Actor.of(source), StringArgumentType.getString(ctx, "name"), skinPlayer));
	}

	private static int remove(CommandContext<CommandSourceStack> ctx, AgentNpc npc) {
		return send(ctx.getSource(), BuilderActions.remove(BuilderActions.Actor.of(ctx.getSource()), npc.npcName()));
	}

	private static int stop(CommandContext<CommandSourceStack> ctx, AgentNpc npc) {
		return send(ctx.getSource(), BuilderActions.stop(BuilderActions.Actor.of(ctx.getSource()), npc.npcName()));
	}

	private static int setPaused(CommandContext<CommandSourceStack> ctx, AgentNpc npc, boolean paused) {
		return send(ctx.getSource(), BuilderActions.setPaused(BuilderActions.Actor.of(ctx.getSource()), npc.npcName(), paused));
	}

	private static int status(CommandContext<CommandSourceStack> ctx, AgentNpc npc) {
		ServerPlayer player = ctx.getSource().getPlayer();
		Optional<BuildTask> build = npc.buildTask();
		if (build.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("<" + npc.npcName() + "> ").append(Component.translatable("aibuilders.npc.idle")), false);
		} else if (player != null) {
			build.get().reportTo(npc, (ServerLevel) npc.level(), player, false);
		} else {
			ctx.getSource().sendSuccess(() -> Component.literal(npc.npcName() + ": ").append(build.get().status()), false);
		}
		return 1;
	}

	private static BlockPos defaultPos(CommandSourceStack source) throws CommandSyntaxException {
		return BuilderActions.lookingPos(source.getPlayerOrException());
	}

	private static int build(CommandContext<CommandSourceStack> ctx, @Nullable BlockPos pos, Rotation rotation, Mirror mirror) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		BlockPos origin = pos != null ? pos : defaultPos(source);
		return send(source, BuilderActions.build(BuilderActions.Actor.of(source),
			StringArgumentType.getString(ctx, "npc"), StringArgumentType.getString(ctx, "schematic"), origin, rotation, mirror));
	}

	private static int preview(CommandContext<CommandSourceStack> ctx, @Nullable BlockPos pos, Rotation rotation, Mirror mirror) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "schematic");
		BlockPos origin = pos != null ? pos : defaultPos(source);
		ServerLevel level = source.getLevel();
		SchematicStore.load(source.getServer(), name).whenCompleteAsync((schematic, error) -> {
			if (error != null) {
				source.sendFailure(Component.translatable("aibuilders.build.load_failed", name, rootMessage(error)));
				return;
			}
			Vec3i size = Schematic.transformedSize(schematic.size(), rotation);
			BlockPos max = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
			PreviewManager.show(level, origin, max, 200);
			source.sendSuccess(() -> Component.translatable("aibuilders.command.preview", name, origin.toShortString(), max.toShortString()), false);
		}, source.getServer());
		return 1;
	}

	private static int info(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "schematic");
		SchematicStore.load(source.getServer(), name).whenCompleteAsync((schematic, error) -> {
			if (error != null) {
				source.sendFailure(Component.translatable("aibuilders.build.load_failed", name, rootMessage(error)));
				return;
			}
			Vec3i s = schematic.size();
			source.sendSuccess(() -> Component.translatable("aibuilders.command.info",
				name, s.getX(), s.getY(), s.getZ(), schematic.countNonAir(), schematic.regions().size()), false);
			if (schematic.skippedBlockEntities() + schematic.skippedEntities() + schematic.unknownBlocks() > 0) {
				source.sendSuccess(() -> Component.translatable("aibuilders.command.info_skipped",
					schematic.skippedBlockEntities(), schematic.skippedEntities(), schematic.unknownBlocks()).withStyle(ChatFormatting.GRAY), false);
			}
		}, source.getServer());
		return 1;
	}

	private static int materials(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		String target = StringArgumentType.getString(ctx, "target");
		Optional<AgentNpc> npc = findNpc(source, target);
		if (npc.isPresent()) {
			Optional<BuildTask> build = npc.get().buildTask();
			ServerPlayer player = source.getPlayer();
			if (build.isEmpty()) {
				source.sendFailure(Component.translatable("aibuilders.npc.idle"));
				return 0;
			}
			if (player != null) {
				build.get().reportTo(npc.get(), (ServerLevel) npc.get().level(), player, true);
			}
			return 1;
		}
		SchematicStore.load(source.getServer(), target).whenCompleteAsync((schematic, error) -> {
			if (error != null) {
				source.sendFailure(Component.translatable("aibuilders.build.load_failed", target, rootMessage(error)));
				return;
			}
			BillOfMaterials bom = new BillOfMaterials();
			schematic.forEachBlock((x, y, z, state) -> bom.add(MaterialResolver.cost(state)));
			List<Map.Entry<Item, Integer>> sorted = bom.sorted();
			source.sendSuccess(() -> Component.translatable("aibuilders.materials.schematic_header", target, sorted.size(), bom.totalItems()).withStyle(ChatFormatting.GOLD), false);
			for (Component line : BillOfMaterials.lines(sorted, MAX_LIST_LINES)) {
				source.sendSuccess(() -> line, false);
			}
			if (bom.unplaceable() > 0) {
				source.sendSuccess(() -> Component.translatable("aibuilders.materials.unplaceable", bom.unplaceable()).withStyle(ChatFormatting.GRAY), false);
			}
		}, source.getServer());
		return 1;
	}

	private static int listSchematics(CommandContext<CommandSourceStack> ctx) {
		List<String> names = SchematicStore.list();
		CommandSourceStack source = ctx.getSource();
		if (names.isEmpty()) {
			source.sendSuccess(() -> Component.translatable("aibuilders.command.no_schematics",
				SchematicStore.roots().get(0).toString(), SchematicStore.roots().get(1).toString()), false);
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("aibuilders.command.schematics", names.size()).withStyle(ChatFormatting.GOLD), false);
		for (String name : names) {
			source.sendSuccess(() -> Component.literal(" - " + name), false);
		}
		return names.size();
	}

	private static String rootMessage(Throwable error) {
		Throwable t = error;
		while (t.getCause() != null && t != t.getCause()) {
			t = t.getCause();
		}
		return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
	}
}
