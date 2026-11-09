package com.bluetwinklez.aibuilders.ai;

import com.bluetwinklez.aibuilders.build.BillOfMaterials;
import com.bluetwinklez.aibuilders.build.BuildTask;
import com.bluetwinklez.aibuilders.build.MaterialResolver;
import com.bluetwinklez.aibuilders.build.schematic.Schematic;
import com.bluetwinklez.aibuilders.build.schematic.SchematicStore;
import com.bluetwinklez.aibuilders.chat.NameMatcher;
import com.bluetwinklez.aibuilders.command.BuilderActions;
import com.bluetwinklez.aibuilders.entity.AgentNpc;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * The only actions the chat model can take. Every call runs with the asking player's own
 * permissions (same rules as {@code /aib}); arguments are untrusted and validated here.
 */
public final class ToolRegistry {
	private static final int MAX_BUILD_DISTANCE = 128;

	private ToolRegistry() {
	}

	public static List<LlmProvider.ToolSpec> specs() {
		return List.of(
			spec("list_npcs", "List builder NPCs the player can control, with what each is doing.", obj()),
			spec("list_schematics", "List schematic names that can be built.", obj()),
			spec("npc_status", "Progress and missing materials of one builder NPC.", obj("npc", str("NPC name"))),
			spec("schematic_materials", "Items needed to build a schematic.", obj("schematic", str("Schematic name"))),
			spec("start_build", "Make an NPC build a schematic. position: \"here\" = where the player stands, "
					+ "\"looking\" = the block the player looks at (default), or \"x y z\" coordinates.",
				obj("npc", str("NPC name"), "schematic", str("Schematic name"), "position", str("here | looking | x y z"),
					"rotation", str("none | clockwise_90 | 180 | counterclockwise_90 (optional)")), "npc", "schematic"),
			spec("stop", "Stop the NPC's current job.", obj("npc", str("NPC name"))),
			spec("pause", "Pause the NPC's current job.", obj("npc", str("NPC name"))),
			spec("resume", "Resume a paused job.", obj("npc", str("NPC name"))),
			spec("spawn_npc", "Create a new builder NPC next to the player.", obj("name", str("New NPC name, one word"),
				"skin", str("Minecraft player name whose skin to use (optional)")), "name")
		);
	}

	/** Runs on the chat worker thread; world access is moved to the server thread. */
	public static AgentLoop.ToolOutcome execute(MinecraftServer server, ServerPlayer player, LlmProvider.ToolCall call,
		Consumer<Component> chatNotes) {
		try {
			JsonObject a = call.arguments();
			return switch (call.name()) {
				case "list_npcs" -> info(onServer(server, () -> {
					List<AgentNpc> npcs = BuilderActions.managedNpcs(BuilderActions.Actor.of(player));
					return npcs.isEmpty() ? "No builder NPCs. Use spawn_npc to create one."
						: String.join("; ", npcs.stream().map(BuilderActions::describe).toList());
				}));
				case "list_schematics" -> {
					List<String> names = SchematicStore.list();
					yield info(names.isEmpty() ? "No schematics on the server." : String.join(", ", names));
				}
				case "npc_status" -> info(onServer(server, () -> withNpc(server, string(a, "npc"), npc -> {
					String text = BuilderActions.describe(npc);
					Optional<BuildTask> build = npc.buildTask();
					if (build.isPresent()) {
						List<Map.Entry<Item, Integer>> missing = build.get().remaining((net.minecraft.server.level.ServerLevel) npc.level()).sorted();
						if (!missing.isEmpty()) {
							text += ". Materials still needed: " + itemList(missing, 8);
						}
					}
					return text;
				})));
				case "schematic_materials" -> info(materials(server, string(a, "schematic")));
				case "start_build" -> startBuild(server, player, a, chatNotes);
				case "stop" -> action(chatNotes, onServer(server, () -> withNpcName(server, string(a, "npc"),
					name -> BuilderActions.stop(BuilderActions.Actor.of(player), name))));
				case "pause" -> action(chatNotes, onServer(server, () -> withNpcName(server, string(a, "npc"),
					name -> BuilderActions.setPaused(BuilderActions.Actor.of(player), name, true))));
				case "resume" -> action(chatNotes, onServer(server, () -> withNpcName(server, string(a, "npc"),
					name -> BuilderActions.setPaused(BuilderActions.Actor.of(player), name, false))));
				case "spawn_npc" -> {
					String name = string(a, "name");
					String skin = a.has("skin") && !string(a, "skin").isBlank() ? string(a, "skin") : name;
					yield action(chatNotes, onServer(server, () -> BuilderActions.spawn(BuilderActions.Actor.of(player), name, skin)));
				}
				default -> info("Error: unknown tool " + call.name());
			};
		} catch (IllegalArgumentException e) {
			return info("Error: " + e.getMessage());
		}
	}

	private static AgentLoop.ToolOutcome startBuild(MinecraftServer server, ServerPlayer player, JsonObject a, Consumer<Component> chatNotes) {
		String schematicQuery = string(a, "schematic");
		NameMatcher.Match schematic = NameMatcher.match(schematicQuery, SchematicStore.list());
		if (!schematic.found()) {
			return info(notFound("schematic", schematicQuery, schematic));
		}
		String position = a.has("position") ? string(a, "position").toLowerCase(Locale.ROOT).strip() : "looking";
		Rotation rotation = rotation(a.has("rotation") ? string(a, "rotation") : "none");
		return action(chatNotes, onServer(server, () -> withNpcName(server, string(a, "npc"), npcName -> {
			BlockPos origin = position(player, position);
			return BuilderActions.build(BuilderActions.Actor.of(player), npcName, schematic.value(), origin, rotation, Mirror.NONE);
		})));
	}

	private static BlockPos position(ServerPlayer player, String position) {
		BlockPos pos = switch (position) {
			case "here", "burasi", "buraya", "my position" -> player.blockPosition();
			case "", "looking", "there", "orasi", "oraya" -> BuilderActions.lookingPos(player);
			default -> {
				String[] parts = position.replace(",", " ").trim().split("\\s+");
				if (parts.length != 3) {
					throw new IllegalArgumentException("position must be here, looking or \"x y z\"");
				}
				try {
					yield new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("position must be here, looking or \"x y z\"");
				}
			}
		};
		if (!pos.closerThan(player.blockPosition(), MAX_BUILD_DISTANCE)) {
			throw new IllegalArgumentException("position is more than " + MAX_BUILD_DISTANCE + " blocks from the player");
		}
		if (player.level().isOutsideBuildHeight(pos)) {
			throw new IllegalArgumentException("position is outside the world height");
		}
		return pos;
	}

	static Rotation rotation(String s) {
		String r = s.toLowerCase(Locale.ROOT).replace(" ", "_");
		return switch (r) {
			case "clockwise_90", "90", "cw", "right", "sag" -> Rotation.CLOCKWISE_90;
			case "180", "clockwise_180" -> Rotation.CLOCKWISE_180;
			case "counterclockwise_90", "270", "-90", "ccw", "left", "sol" -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	private static String materials(MinecraftServer server, String query) {
		NameMatcher.Match match = NameMatcher.match(query, SchematicStore.list());
		if (!match.found()) {
			return notFound("schematic", query, match);
		}
		try {
			Schematic schematic = SchematicStore.load(server, match.value()).get(20, TimeUnit.SECONDS);
			BillOfMaterials bom = new BillOfMaterials();
			schematic.forEachBlock((x, y, z, state) -> bom.add(MaterialResolver.cost(state)));
			return "'" + match.value() + "' needs " + bom.totalItems() + " items: " + itemList(bom.sorted(), 10);
		} catch (Exception e) {
			return "Error: could not load schematic " + match.value();
		}
	}

	private static String itemList(List<Map.Entry<Item, Integer>> entries, int max) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < Math.min(max, entries.size()); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(entries.get(i).getValue()).append("x ").append(new ItemStack(entries.get(i).getKey()).getHoverName().getString());
		}
		if (entries.size() > max) {
			sb.append(" and ").append(entries.size() - max).append(" more kinds");
		}
		return sb.toString();
	}

	/** Resolves a fuzzy NPC name among NPCs on the server. Server thread. */
	private static <T> T withNpcName(MinecraftServer server, String query, java.util.function.Function<String, T> action) {
		List<String> names = BuilderActions.allNpcs(server).stream().map(AgentNpc::npcName).toList();
		NameMatcher.Match match = NameMatcher.match(query, names);
		if (!match.found()) {
			throw new IllegalArgumentException(notFound("NPC", query, match));
		}
		return action.apply(match.value());
	}

	private static String withNpc(MinecraftServer server, String query, java.util.function.Function<AgentNpc, String> action) {
		return withNpcName(server, query, name -> action.apply(BuilderActions.findNpc(server, name).orElseThrow()));
	}

	private static String notFound(String what, String query, NameMatcher.Match match) {
		String known = String.join(", ", match.candidates());
		return match.kind() == NameMatcher.Kind.AMBIGUOUS
			? what + " '" + query + "' is ambiguous, could be: " + known
			: "no " + what + " named '" + query + "'" + (known.isEmpty() ? "" : ". Known: " + known);
	}

	private static <T> T onServer(MinecraftServer server, Supplier<T> task) {
		try {
			return CompletableFuture.supplyAsync(task, server).get(10, TimeUnit.SECONDS);
		} catch (java.util.concurrent.ExecutionException e) {
			if (e.getCause() instanceof IllegalArgumentException iae) {
				throw iae;
			}
			throw new IllegalArgumentException(e.getCause() == null ? e.toString() : e.getCause().toString());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalArgumentException("interrupted");
		} catch (java.util.concurrent.TimeoutException e) {
			throw new IllegalArgumentException("server did not respond in time");
		}
	}

	private static AgentLoop.ToolOutcome info(String text) {
		return new AgentLoop.ToolOutcome(text, null);
	}

	/** The model gets plain text; players get the translatable message so they see their own language. */
	private static AgentLoop.ToolOutcome action(Consumer<Component> chatNotes, BuilderActions.Result result) {
		if (result.ok()) {
			chatNotes.accept(result.message());
		}
		return new AgentLoop.ToolOutcome((result.ok() ? "OK: " : "Failed: ") + result.message().getString(), null);
	}

	private static String string(JsonObject a, String key) {
		JsonElement e = a.get(key);
		if (e == null || e.isJsonNull()) {
			throw new IllegalArgumentException("missing argument '" + key + "'");
		}
		if (e.isJsonPrimitive()) {
			return e.getAsString().strip();
		}
		if (e.isJsonObject() && key.equals("position")) {
			JsonObject p = e.getAsJsonObject();
			return p.get("x").getAsInt() + " " + p.get("y").getAsInt() + " " + p.get("z").getAsInt();
		}
		return e.toString();
	}

	// --- JSON schema helpers ---------------------------------------------------------

	private static LlmProvider.ToolSpec spec(String name, String description, JsonObject parameters, String... required) {
		if (required.length > 0) {
			com.google.gson.JsonArray req = new com.google.gson.JsonArray();
			for (String r : required) {
				req.add(r);
			}
			parameters.add("required", req);
		} else if (parameters.getAsJsonObject("properties").size() > 0) {
			com.google.gson.JsonArray req = new com.google.gson.JsonArray();
			parameters.getAsJsonObject("properties").keySet().forEach(req::add);
			parameters.add("required", req);
		}
		return new LlmProvider.ToolSpec(name, description, parameters);
	}

	private static JsonObject str(String description) {
		JsonObject p = new JsonObject();
		p.addProperty("type", "string");
		p.addProperty("description", description);
		return p;
	}

	private static JsonObject obj(Object... nameAndSchema) {
		JsonObject props = new JsonObject();
		for (int i = 0; i < nameAndSchema.length; i += 2) {
			props.add((String) nameAndSchema[i], (JsonObject) nameAndSchema[i + 1]);
		}
		JsonObject schema = new JsonObject();
		schema.addProperty("type", "object");
		schema.add("properties", props);
		return schema;
	}
}
