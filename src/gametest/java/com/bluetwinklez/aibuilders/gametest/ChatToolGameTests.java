package com.bluetwinklez.aibuilders.gametest;

import com.bluetwinklez.aibuilders.ai.LlmProvider;
import com.bluetwinklez.aibuilders.build.BuildTask;
import com.bluetwinklez.aibuilders.chat.ChatHandler;
import com.bluetwinklez.aibuilders.config.AiBuildersConfig;
import com.bluetwinklez.aibuilders.entity.AgentNpc;
import com.bluetwinklez.aibuilders.entity.ModEntities;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/** "Claude ..." chat: model tool calls and the offline rule-based path drive real NPCs. */
public class ChatToolGameTests {
	private static final String ARENA = "aibuilders-gametest:arena";

	/** Plays back fixed responses; counts calls so tests know the worker has finished. */
	private static final class Scripted implements LlmProvider {
		final Deque<Response> script = new ArrayDeque<>();
		final AtomicInteger calls = new AtomicInteger();

		Scripted then(String text, String tool, String argsJson) {
			JsonObject args = argsJson == null ? new JsonObject() : JsonParser.parseString(argsJson).getAsJsonObject();
			script.add(new Response(text, tool == null ? List.of() : List.of(new ToolCall("t" + script.size(), tool, args))));
			return this;
		}

		@Override
		public Response chat(String system, List<Message> history, List<ToolSpec> tools) {
			calls.incrementAndGet();
			return script.isEmpty() ? new Response("ok", List.of()) : script.removeFirst();
		}
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
		player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		return player;
	}

	private static AgentNpc npc(GameTestHelper helper, String name, UUID owner) {
		AgentNpc npc = helper.spawn(ModEntities.BUILDER, new BlockPos(5, 1, 5));
		npc.setup(name, "Steve", owner);
		return npc;
	}

	private static void floor(GameTestHelper helper) {
		AiBuildersConfig.get().showBossBar = false;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static void schematic(String name) {
		BuilderGameTests.writeStructurePublic(name, 2, 1, 1, Map.of(
			new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState(), new BlockPos(1, 0, 0), Blocks.STONE.defaultBlockState()));
	}

	/** The model asks for start_build with sloppy names; the owner's NPC starts building. */
	@GameTest(structure = ARENA, maxTicks = 200)
	public void modelToolCallStartsBuild(GameTestHelper helper) {
		floor(helper);
		schematic("gt_chat_build");
		ServerPlayer player = player(helper);
		AgentNpc npc = npc(helper, "Chatbuilder", player.getUUID());
		Scripted model = new Scripted()
			.then("", "start_build", "{\"npc\":\"chatbuilder\",\"schematic\":\"GT CHAT BUILD\",\"position\":\"here\"}")
			.then("Chatbuilder başladı.", null, null);
		ChatHandler.TEST_PROVIDERS.put(player.getUUID(), Optional.of(model));
		ChatHandler.ask(player, "Chatbuilder gt_chat_build şemasını buraya kursun");
		helper.succeedWhen(() -> {
			helper.assertValueEqual(model.calls.get(), 2, "model calls");
			BuildTask task = npc.buildTask().orElseThrow(() -> helper.assertionException("NPC did not start building"));
			helper.assertValueEqual(task.schematicName(), "gt_chat_build", "schematic");
		});
	}

	/** A player who does not own the NPC cannot stop it through the model either. */
	@GameTest(structure = ARENA, maxTicks = 200)
	public void modelCannotBypassOwnership(GameTestHelper helper) {
		floor(helper);
		schematic("gt_chat_owned");
		ServerPlayer player = player(helper);
		helper.assertFalse(player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER), "mock player must not be an operator");
		AgentNpc npc = npc(helper, "Ownedbuilder", UUID.randomUUID());
		npc.taskRunner().start(new BuildTask("gt_chat_owned", helper.absolutePos(new BlockPos(10, 1, 10)), Mirror.NONE, Rotation.NONE,
			AiBuildersConfig.BuildMode.SURVIVAL), npc, helper.getLevel());
		Scripted model = new Scripted().then("", "stop", "{\"npc\":\"Ownedbuilder\"}").then("Yapamıyorum.", null, null);
		ChatHandler.TEST_PROVIDERS.put(player.getUUID(), Optional.of(model));
		ChatHandler.ask(player, "Ownedbuilder'ı durdur");
		helper.succeedWhen(() -> {
			helper.assertValueEqual(model.calls.get(), 2, "model calls");
			helper.assertTrue(npc.taskRunner().hasTask(), "someone else's NPC must keep working");
		});
	}

	/** Without a model, "<npc> <schematic> kur" is still understood. */
	@GameTest(structure = ARENA, maxTicks = 200)
	public void offlineIntentStartsBuild(GameTestHelper helper) {
		floor(helper);
		schematic("gt_chat_offline");
		ServerPlayer player = player(helper);
		AgentNpc npc = npc(helper, "Offlinebuilder", player.getUUID());
		ChatHandler.TEST_PROVIDERS.put(player.getUUID(), Optional.empty());
		ChatHandler.ask(player, "Offlinebuilder gt_chat_offline'ı buraya kursun");
		helper.succeedWhen(() -> {
			BuildTask task = npc.buildTask().orElseThrow(() -> helper.assertionException("NPC did not start building"));
			helper.assertValueEqual(task.schematicName(), "gt_chat_offline", "schematic");
		});
	}
}
