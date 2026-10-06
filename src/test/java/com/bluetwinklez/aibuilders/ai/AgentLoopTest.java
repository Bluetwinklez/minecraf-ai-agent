package com.bluetwinklez.aibuilders.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentLoopTest {
	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	/** Replays scripted responses and records what the model was shown. */
	private static final class ScriptedProvider implements LlmProvider {
		final Deque<Response> script = new ArrayDeque<>();
		final List<List<Message>> seen = new ArrayList<>();
		final List<Integer> toolCounts = new ArrayList<>();

		@Override
		public Response chat(String system, List<Message> history, List<ToolSpec> tools) {
			seen.add(List.copyOf(history));
			toolCounts.add(tools.size());
			return script.removeFirst();
		}
	}

	private static final List<LlmProvider.ToolSpec> TOOLS = List.of(new LlmProvider.ToolSpec("start_build", "d", json("{\"type\":\"object\"}")));

	@Test
	void executesToolsAndFeedsResultsBack() throws Exception {
		ScriptedProvider p = new ScriptedProvider();
		p.script.add(new LlmProvider.Response("", List.of(new LlmProvider.ToolCall("c1", "start_build", json("{\"npc\":\"Ali\"}")))));
		p.script.add(new LlmProvider.Response("Ali başladı.", List.of()));
		List<String> executed = new ArrayList<>();
		AgentLoop.Result r = AgentLoop.run(p, "sys", List.of(new LlmProvider.Message("user", "Ali kursun")), TOOLS, call -> {
			executed.add(call.name() + call.arguments());
			return new AgentLoop.ToolOutcome("OK: started", "Ali started");
		}, 3);
		assertEquals("Ali başladı.", r.text());
		assertEquals(List.of("Ali started"), r.notes());
		assertEquals(List.of("start_build{\"npc\":\"Ali\"}"), executed);
		List<LlmProvider.Message> second = p.seen.get(1);
		assertEquals("assistant", second.get(1).role());
		assertEquals("tool", second.get(2).role());
		assertEquals("c1", second.get(2).toolCallId());
		assertEquals("OK: started", second.get(2).content());
	}

	@Test
	void lastRoundOffersNoToolsSoTheLoopEnds() throws Exception {
		ScriptedProvider p = new ScriptedProvider();
		LlmProvider.ToolCall call = new LlmProvider.ToolCall("c", "start_build", new JsonObject());
		p.script.add(new LlmProvider.Response("", List.of(call)));
		p.script.add(new LlmProvider.Response("", List.of(call)));
		p.script.add(new LlmProvider.Response("done", List.of()));
		AgentLoop.Result r = AgentLoop.run(p, "sys", List.of(new LlmProvider.Message("user", "x")), TOOLS,
			c -> new AgentLoop.ToolOutcome("Failed: nope", null), 2);
		assertEquals("done", r.text());
		assertEquals(List.of(1, 1, 0), p.toolCounts);
		assertTrue(r.notes().isEmpty());
	}
}
