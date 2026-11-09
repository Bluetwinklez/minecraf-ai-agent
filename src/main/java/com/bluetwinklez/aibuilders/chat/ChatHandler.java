package com.bluetwinklez.aibuilders.chat;

import com.bluetwinklez.aibuilders.AiBuilders;
import com.bluetwinklez.aibuilders.ai.ClaudeProvider;
import com.bluetwinklez.aibuilders.ai.AgentLoop;
import com.bluetwinklez.aibuilders.ai.ToolRegistry;
import com.bluetwinklez.aibuilders.build.schematic.SchematicStore;
import com.bluetwinklez.aibuilders.command.BuilderActions;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import com.bluetwinklez.aibuilders.ai.LlmProvider;
import com.bluetwinklez.aibuilders.ai.OllamaProvider;
import com.bluetwinklez.aibuilders.build.BillOfMaterials;
import com.bluetwinklez.aibuilders.build.BuildTask;
import com.bluetwinklez.aibuilders.config.AiBuildersConfig;
import com.bluetwinklez.aibuilders.entity.AgentNpc;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/** Reacts to player chat: "sa" greetings and "Claude ..." questions. */
public final class ChatHandler {
	private static final int HISTORY_MESSAGES = 6;
	private static final int GREETING_COOLDOWN_TICKS = 200;
	private static final int MAX_QUEUED = 5;

	/** One worker: requests run one after another so a small VDS is never flooded. */
	private static final ExecutorService WORKER = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
		new LinkedBlockingQueue<>(MAX_QUEUED), r -> {
			Thread t = new Thread(r, "AI Builders chat");
			t.setDaemon(true);
			return t;
		});
	private static final Map<UUID, Deque<LlmProvider.Message>> HISTORY = new HashMap<>();
	private static final Map<UUID, Long> LAST_GREETING = new HashMap<>();
	private static final Map<UUID, Long> LAST_QUESTION = new HashMap<>();
	private static boolean warnedOps;

	private ChatHandler() {
	}

	/** Fabric {@code ServerMessageEvents.CHAT_MESSAGE} callback; runs on the server thread. */
	public static void onChat(PlayerChatMessage message, ServerPlayer player, ChatType.Bound bound) {
		String text = message.signedContent();
		AiBuildersConfig config = AiBuildersConfig.get();
		if (config.chatEnabled) {
			Optional<String> question = ChatTriggers.triggered(config.chatTriggerWord, text);
			if (question.isPresent()) {
				ask(player, question.get());
				return;
			}
		}
		if (config.greetings) {
			ChatTriggers.greeting(text).ifPresent(g -> greet(player, g));
		}
	}

	private static void greet(ServerPlayer player, ChatTriggers.Greeting greeting) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long last = LAST_GREETING.get(player.getUUID());
		if (last != null && now - last < GREETING_COOLDOWN_TICKS) {
			return;
		}
		AgentNpc npc = nearestNpc(player);
		if (npc == null) {
			return;
		}
		LAST_GREETING.put(player.getUUID(), now);
		npc.getLookControl().setLookAt(player);
		String key = greeting == ChatTriggers.Greeting.SELAMUN_ALEYKUM ? "aibuilders.chat.as" : "aibuilders.chat.hello";
		npc.say(level, Component.translatable(key, player.getName()), AiBuildersConfig.get().chatRadius);
	}

	private static @Nullable AgentNpc nearestNpc(ServerPlayer player) {
		double r = AiBuildersConfig.get().chatRadius;
		return player.level().getEntities(EntityTypeTest.forClass(AgentNpc.class), npc -> npc.isAlive() && npc.distanceToSqr(player) <= r * r)
			.stream()
			.min(Comparator.comparingDouble(npc -> npc.distanceToSqr(player)))
			.orElse(null);
	}

	// --- "Claude ..." -------------------------------------------------------------

	/**
	 * Test hook: per-player provider replacing the configured one (empty Optional = no model,
	 * i.e. the offline path). Keyed by player so parallel GameTests do not interfere.
	 */
	public static final Map<UUID, Optional<LlmProvider>> TEST_PROVIDERS = new java.util.concurrent.ConcurrentHashMap<>();

	/** Handles one "Claude ..." question from {@code player}. Server thread. */
	public static void ask(ServerPlayer player, String question) {
		MinecraftServer server = player.level().getServer();
		AiBuildersConfig config = AiBuildersConfig.get();
		long now = server.getTickCount();
		Long last = LAST_QUESTION.get(player.getUUID());
		if (last != null && now - last < config.chatCooldownSeconds * 20L) {
			player.sendSystemMessage(prefix().append(Component.translatable("aibuilders.chat.cooldown")).withStyle(ChatFormatting.GRAY));
			return;
		}
		LAST_QUESTION.put(player.getUUID(), now);
		if (question.isBlank()) {
			question = "?";
		}

		// Everything that touches the world is read here, on the server thread.
		AgentNpc npc = nearestNpc(player);
		String context = context(player, npc);
		String fallback = cannedAnswer(question, player, npc);
		String finalQuestion = question;
		Runnable offline = () -> answerOffline(player, finalQuestion, fallback);
		String system = systemPrompt(player, context, config.chatToolsEnabled);
		Deque<LlmProvider.Message> history = HISTORY.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
		history.addLast(new LlmProvider.Message("user", question));
		trim(history);
		List<LlmProvider.Message> snapshot = new ArrayList<>(history);
		Optional<LlmProvider> testProvider = TEST_PROVIDERS.get(player.getUUID());
		LlmProvider provider = testProvider != null ? testProvider.orElse(null) : provider(config);
		UUID playerId = player.getUUID();

		if (provider == null) {
			history.clear();
			offline.run();
			return;
		}
		List<LlmProvider.ToolSpec> tools = config.chatToolsEnabled ? ToolRegistry.specs() : List.of();
		try {
			WORKER.execute(() -> {
				List<Component> notes = java.util.Collections.synchronizedList(new ArrayList<>());
				try {
					AgentLoop.Result result = AgentLoop.run(provider, system, snapshot, tools,
						call -> ToolRegistry.execute(server, player, call, notes::add), Math.max(0, config.maxToolRounds));
					server.execute(() -> {
						if (!result.text().isBlank()) {
							answer(server, playerId, result.text(), true);
						}
						for (Component note : notes) {
							server.getPlayerList().broadcastSystemMessage(
								(result.text().isBlank() ? prefix() : Component.literal("  \u2192 ")).append(note.copy()).withStyle(ChatFormatting.GRAY), false);
						}
						if (result.text().isBlank() && notes.isEmpty()) {
							answer(server, playerId, "?", false);
						}
					});
				} catch (Exception e) {
					AiBuilders.LOGGER.warn("Chat provider '{}' failed: {}", config.provider, e.toString());
					server.execute(() -> {
						warnOpsOnce(server, e);
						history.clear();
						offline.run();
					});
				}
			});
		} catch (RejectedExecutionException e) {
			history.pollLast();
			player.sendSystemMessage(prefix().append(Component.translatable("aibuilders.chat.busy")).withStyle(ChatFormatting.GRAY));
		}
	}

	/** No model: try to understand a simple command, otherwise give the canned answer. Server thread. */
	private static void answerOffline(ServerPlayer player, String question, String fallback) {
		MinecraftServer server = player.level().getServer();
		BuilderActions.Actor actor = BuilderActions.Actor.of(player);
		List<String> npcNames = BuilderActions.allNpcs(server).stream().map(AgentNpc::npcName).toList();
		Optional<IntentParser.Intent> intent = IntentParser.parse(question, npcNames, SchematicStore.list());
		if (intent.isEmpty() || intent.get().action() == IntentParser.Action.STATUS && intent.get().npc() == null
			|| intent.get().action() == IntentParser.Action.MATERIALS) {
			answer(server, player.getUUID(), fallback, false);
			return;
		}
		IntentParser.Intent in = intent.get();
		String npcName = in.npc();
		if (npcName == null) {
			List<AgentNpc> mine = BuilderActions.managedNpcs(actor);
			if (mine.size() != 1) {
				answer(server, player.getUUID(), mine.isEmpty()
					? "Önce bir inşaatçı oluştur: /aib spawn <isim>"
					: "Hangi inşaatçı? " + String.join(", ", mine.stream().map(AgentNpc::npcName).toList()), false);
				return;
			}
			npcName = mine.getFirst().npcName();
		}
		BuilderActions.Result result = switch (in.action()) {
			case BUILD -> BuilderActions.build(actor, npcName, in.schematic(),
				in.here() ? player.blockPosition() : BuilderActions.lookingPos(player), Rotation.NONE, Mirror.NONE);
			case STOP -> BuilderActions.stop(actor, npcName);
			case PAUSE -> BuilderActions.setPaused(actor, npcName, true);
			case RESUME -> BuilderActions.setPaused(actor, npcName, false);
			default -> null;
		};
		if (result == null) {
			String name = npcName;
			answer(server, player.getUUID(), BuilderActions.findNpc(server, name).map(BuilderActions::describe).orElse(fallback), false);
			return;
		}
		server.getPlayerList().broadcastSystemMessage(prefix().append(result.message().copy()), false);
	}

	private static void answer(MinecraftServer server, UUID playerId, String reply, boolean remember) {
		String clean = clean(reply);
		if (clean.length() > 400) {
			clean = clean.substring(0, 400) + "...";
		}
		Deque<LlmProvider.Message> history = HISTORY.get(playerId);
		if (history != null) {
			if (remember) {
				history.addLast(new LlmProvider.Message("assistant", clean));
				trim(history);
			} else {
				history.clear();
			}
		}
		server.getPlayerList().broadcastSystemMessage(prefix().append(Component.literal(clean)), false);
	}

	/** Model output is untrusted: drop legacy formatting codes and control characters. */
	private static String clean(String text) {
		return text.replace('\u00a7', ' ').replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").strip();
	}

	private static void trim(Deque<LlmProvider.Message> history) {
		while (history.size() > HISTORY_MESSAGES) {
			history.pollFirst();
		}
		// Claude requires the conversation to start with a user turn.
		while (!history.isEmpty() && !history.peekFirst().role().equals("user")) {
			history.pollFirst();
		}
	}

	private static @Nullable LlmProvider provider(AiBuildersConfig config) {
		return switch (config.provider.toLowerCase(Locale.ROOT)) {
			case "ollama" -> new OllamaProvider(config.ollamaUrl, config.ollamaModel, config.maxTokens, config.chatTimeoutSeconds);
			case "claude" -> new ClaudeProvider(config.resolvedClaudeApiKey(), config.claudeModel, config.maxTokens, config.chatTimeoutSeconds);
			default -> null;
		};
	}

	private static void warnOpsOnce(MinecraftServer server, Exception e) {
		if (warnedOps) {
			return;
		}
		warnedOps = true;
		Component warning = prefix().append(Component.translatable("aibuilders.chat.provider_down", AiBuildersConfig.get().provider, e.getMessage() == null ? e.toString() : e.getMessage()))
			.withStyle(ChatFormatting.RED);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
				p.sendSystemMessage(warning);
			}
		}
	}

	private static net.minecraft.network.chat.MutableComponent prefix() {
		return Component.literal("[" + AiBuildersConfig.get().chatTriggerWord + "] ").withStyle(ChatFormatting.AQUA);
	}

	private static String systemPrompt(ServerPlayer player, String context, boolean tools) {
		String actions = tools
			? """
				You can act through the provided tools: list NPCs and schematics, check status and materials, start, pause, resume
				or stop builds, and spawn builders. Only use the tools; never invent results. If the NPC or schematic name is unclear,
				call list_npcs or list_schematics first. If a tool fails, tell the player why in one sentence.
				For start_build use position "here" when the player says here/buraya, otherwise "looking"."""
			: """
				You cannot run commands or control NPCs yourself; if asked, explain which /aib command the player can use
				(spawn, build, preview, materials, status, pause, resume, stop, schematics).""";
		return """
			You are %s, a helpful assistant inside a Minecraft server, talking in the public chat.
			Answer in the same language the player used. Keep answers very short: at most two sentences, plain text, no markdown.
			%s
			Player: %s
			%s""".formatted(AiBuildersConfig.get().chatTriggerWord, actions, player.getName().getString(), context);
	}

	/** Facts about the nearest builder NPC so the model can answer "how many planks do we need?". */
	private static String context(ServerPlayer player, @Nullable AgentNpc npc) {
		if (npc == null) {
			return "No builder NPC is near the player.";
		}
		StringBuilder sb = new StringBuilder("Nearest builder NPC: ").append(npc.npcName());
		Optional<BuildTask> build = npc.buildTask();
		if (build.isEmpty()) {
			return sb.append(" (idle).").toString();
		}
		sb.append(", building '").append(build.get().schematicName()).append("', ")
			.append(Math.round(build.get().progress() * 100)).append("% done");
		List<Map.Entry<Item, Integer>> missing = build.get().remaining(player.level()).sorted();
		if (!missing.isEmpty()) {
			sb.append(". Materials still needed for the rest: ");
			for (int i = 0; i < Math.min(8, missing.size()); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				sb.append(missing.get(i).getValue()).append("x ").append(new ItemStack(missing.get(i).getKey()).getHoverName().getString());
			}
		}
		return sb.append('.').toString();
	}

	/** Answer used when no model is configured or the model is unreachable. */
	static String cannedAnswer(String question, ServerPlayer player, @Nullable AgentNpc npc) {
		String q = ChatTriggers.normalize(question);
		// Default to Turkish unless the question is clearly English.
		boolean turkish = !q.matches(".*\\b(what|how|many|need|missing|status|help|hello|hi|the|is|are|you)\\b.*");
		if (q.contains("test")) {
			return turkish ? "Test başarılı, buradayım." : "Test OK, I'm here.";
		}
		if (npc != null && npc.buildTask().isPresent() && q.matches(".*\\b(malzeme|eksik|lazim|kac|material|missing|need)\\b.*")) {
			List<Map.Entry<Item, Integer>> missing = npc.buildTask().get().remaining(player.level()).sorted();
			if (missing.isEmpty()) {
				return turkish ? npc.npcName() + " için eksik malzeme yok." : npc.npcName() + " has everything it needs.";
			}
			StringBuilder sb = new StringBuilder(turkish ? npc.npcName() + " için gerekenler: " : npc.npcName() + " needs: ");
			for (int i = 0; i < Math.min(5, missing.size()); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				sb.append(missing.get(i).getValue()).append("x ").append(new ItemStack(missing.get(i).getKey()).getHoverName().getString());
			}
			return sb.toString();
		}
		if (npc != null && npc.buildTask().isPresent()) {
			BuildTask build = npc.buildTask().get();
			int percent = Math.round(build.progress() * 100);
			return turkish
				? npc.npcName() + " '" + build.schematicName() + "' inşa ediyor: %" + percent + (build.isWaiting() ? " (malzeme bekliyor)" : "")
				: npc.npcName() + " is building '" + build.schematicName() + "': " + percent + "%" + (build.isWaiting() ? " (waiting for materials)" : "");
		}
		return turkish
			? "Yapay zeka şu an kapalı. Komutlar: /aib spawn, /aib build, /aib materials, /aib status."
			: "AI chat is offline. Commands: /aib spawn, /aib build, /aib materials, /aib status.";
	}
}
